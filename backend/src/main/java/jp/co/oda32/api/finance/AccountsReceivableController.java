package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.AccountsReceivableCutoffReconciler;
import jp.co.oda32.batch.finance.service.InvoiceVerifier;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.finance.SalesJournalCsvService;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.AccountsReceivableAggregateRequest;
import jp.co.oda32.dto.finance.AccountsReceivableBulkVerifyRequest;
import jp.co.oda32.dto.finance.AccountsReceivableBulkVerifyResponse;
import jp.co.oda32.dto.finance.AccountsReceivableResponse;
import jp.co.oda32.dto.finance.AccountsReceivableSummaryResponse;
import jp.co.oda32.dto.finance.AccountsReceivableVerifyRequest;
import jp.co.oda32.dto.finance.MfExportToggleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 売掛金一覧画面のAPIコントローラ。
 * <p>
 * 設計: {@code claudedocs/design-accounts-receivable-mf.md}
 * 買掛側 {@link FinanceController} の {@code /accounts-payable} 系と対称構造。
 *
 * @since 2026/04/17
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/accounts-receivable")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AccountsReceivableController {

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Charset CP932 = Charset.forName("windows-31j");

    private final TAccountsReceivableSummaryService summaryService;
    private final MPartnerService mPartnerService;
    private final InvoiceVerifier invoiceVerifier;
    private final AccountsReceivableCutoffReconciler cutoffReconciler;
    private final SalesJournalCsvService salesJournalCsvService;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Qualifier("batchTaskExecutor")
    private final ThreadPoolTaskExecutor batchExecutor;

    // -------- 一覧 --------

    @GetMapping
    public ResponseEntity<Page<AccountsReceivableResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String verificationFilter,
            @PageableDefault(size = 50, sort = "transactionMonth", direction = Sort.Direction.DESC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        Page<TAccountsReceivableSummary> page = summaryService.findPaged(
                effectiveShopNo, partnerNo, fromDate, toDate, verificationFilter, pageable);

        Set<Integer> partnerNos = page.getContent().stream()
                .map(TAccountsReceivableSummary::getPartnerNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, MPartner> partnerMap = partnerNos.stream()
                .map(mPartnerService::getByPartnerNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MPartner::getPartnerNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ar -> AccountsReceivableResponse.from(ar, partnerMap.get(ar.getPartnerNo()))));
    }

    @GetMapping("/summary")
    public ResponseEntity<AccountsReceivableSummaryResponse> summary(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        return ResponseEntity.ok(summaryService.summary(effectiveShopNo, fromDate, toDate));
    }

    // -------- 再集計 --------

    @PostMapping("/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> aggregate(
            @Valid @RequestBody AccountsReceivableAggregateRequest request) {
        try {
            LocalDate.parse(request.getTargetDate(), YYYY_MM_DD);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "targetDate は yyyyMMdd 形式で指定してください"));
        }
        String cutoffType = request.getCutoffType();
        if (cutoffType == null || cutoffType.isBlank()) {
            cutoffType = "all";
        }
        if (!Set.of("all", "15", "20", "month_end").contains(cutoffType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "cutoffType は all / 15 / 20 / month_end のいずれかを指定してください"));
        }

        String beanName = "accountsReceivableSummaryJob";
        if (!applicationContext.containsBean(beanName)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "集計ジョブが見つかりません"));
        }
        Job job = (Job) applicationContext.getBean(beanName);

        JobParametersBuilder params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("targetDate", request.getTargetDate())
                .addString("cutoffType", cutoffType);

        var jobParams = params.toJobParameters();
        final String cutoffTypeCapture = cutoffType;
        batchExecutor.submit(() -> {
            try {
                jobLauncher.run(job, jobParams);
            } catch (Exception e) {
                log.error("売掛金集計ジョブの非同期実行に失敗: targetDate={}, cutoffType={}",
                        request.getTargetDate(), cutoffTypeCapture, e);
            }
        });

        return ResponseEntity.accepted()
                .body(Map.of(
                        "status", "STARTED",
                        "targetDate", request.getTargetDate(),
                        "cutoffType", cutoffType));
    }

    // -------- 一括検証 --------

    @PostMapping("/bulk-verify")
    public ResponseEntity<AccountsReceivableBulkVerifyResponse> bulkVerify(
            @Valid @RequestBody AccountsReceivableBulkVerifyRequest request) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(request.getShopNo());
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByShopAndDateRange(effectiveShopNo, request.getFromDate(), request.getToDate());

        // 事前パス: 請求書の closing_date と AR の transaction_month が食い違う得意先を
        // 自動で再集計する（マスタ cutoff_date の更新が追いついていないケースへの自動追従）。
        AccountsReceivableCutoffReconciler.ReconcileResult reconcile =
                cutoffReconciler.reconcile(summaries, request.getFromDate(), request.getToDate());
        if (reconcile.getReconciledPartners() > 0) {
            // 再集計で AR が変わっているので再ロード
            summaries = summaryService
                    .findByShopAndDateRange(effectiveShopNo, request.getFromDate(), request.getToDate());
            log.info("bulkVerify: 自動再集計 partners={} inserted={} deleted={}",
                    reconcile.getReconciledPartners(), reconcile.getInsertedRows(), reconcile.getDeletedRows());
        }

        InvoiceVerificationSummary result = invoiceVerifier.verify(summaries, request.getToDate());

        // 結果を保存
        for (TAccountsReceivableSummary s : summaries) {
            summaryService.save(s);
        }

        return ResponseEntity.ok(AccountsReceivableBulkVerifyResponse.builder()
                .matchedCount(result.getMatchedCount())
                .mismatchCount(result.getMismatchCount())
                .notFoundCount(result.getNotFoundCount())
                .skippedManualCount(result.getSkippedManualCount())
                .josamaOverwriteCount(result.getJosamaOverwriteCount())
                .quarterlySpecialCount(result.getQuarterlySpecialCount())
                .reconciledPartners(reconcile.getReconciledPartners())
                .reconciledDeletedRows(reconcile.getDeletedRows())
                .reconciledInsertedRows(reconcile.getInsertedRows())
                .reconciledSkippedManualPartners(reconcile.getSkippedManualPartners())
                .reconciledDetails(reconcile.getReconciledDetails())
                .build());
    }

    // -------- 手動確定 --------

    @PutMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/verify")
    public ResponseEntity<AccountsReceivableResponse> verify(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag,
            @Valid @RequestBody AccountsReceivableVerifyRequest request) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.verify(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag,
                request.getTaxIncludedAmount(), request.getTaxExcludedAmount(),
                request.getNote(),
                request.getMfExportEnabled() != null ? request.getMfExportEnabled() : true);
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    @DeleteMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/manual-lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsReceivableResponse> releaseManualLock(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.releaseManualLock(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    @PatchMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/mf-export")
    public ResponseEntity<AccountsReceivableResponse> toggleMfExport(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag,
            @Valid @RequestBody MfExportToggleRequest request) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.updateMfExport(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag, request.getEnabled());
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    // -------- 検証済みCSV DL --------

    @GetMapping("/export-mf-csv")
    public ResponseEntity<InputStreamResource> exportMfCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long initialTransactionNo) throws Exception {
        if (fromDate == null || toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate と toDate は必須です");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate は toDate 以前を指定してください");
        }
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByDateRangeAndMfExportEnabled(fromDate, toDate, true);
        log.info("売掛金→売上仕訳CSV DL: fromDate={}, toDate={}, 件数={}", fromDate, toDate, summaries.size());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CP932)) {
            salesJournalCsvService.writeCsv(summaries, w, initialTransactionNo);
        }

        // CSV出力済みマーカーを付ける（買掛と同じ仕様）
        salesJournalCsvService.markExported(summaries);
        for (TAccountsReceivableSummary s : summaries) {
            summaryService.save(s);
        }

        String filename = generateFileName(fromDate, toDate);
        byte[] bytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                .contentLength(bytes.length)
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    private String generateFileName(LocalDate from, LocalDate to) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (from.equals(to)) {
            return "accounts_receivable_to_sales_journal_" + from.format(f) + ".csv";
        }
        return "accounts_receivable_to_sales_journal_" + from.format(f) + "_" + to.format(f) + ".csv";
    }

    private void assertShopAccess(Integer shopNo) {
        Integer effective = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        if (effective != null && !effective.equals(shopNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
        }
    }
}
