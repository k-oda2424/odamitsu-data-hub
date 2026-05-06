package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.AccountsReceivableCutoffReconciler;
import jp.co.oda32.domain.model.finance.CutoffType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.finance.AccountsReceivableBulkVerifyService;
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
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
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

    private final TAccountsReceivableSummaryService summaryService;
    private final MPartnerService mPartnerService;
    private final AccountsReceivableBulkVerifyService bulkVerifyService;
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
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String verificationFilter,
            @PageableDefault(size = 50, sort = "transactionMonth", direction = Sort.Direction.DESC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        Page<TAccountsReceivableSummary> page = summaryService.findPaged(
                effectiveShopNo, partnerNo, partnerCode, fromDate, toDate, verificationFilter, pageable);

        Set<Integer> partnerNos = page.getContent().stream()
                .map(TAccountsReceivableSummary::getPartnerNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // SF-E11: 1 行ごとに getByPartnerNo (= findById) を呼ぶと N+1 になるため bulk fetch + Map 化。
        Map<Integer, MPartner> partnerMap = mPartnerService.findAllByPartnerNos(partnerNos).stream()
                .collect(Collectors.toMap(MPartner::getPartnerNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ar -> AccountsReceivableResponse.from(ar, partnerMap.get(ar.getPartnerNo()))));
    }

    @GetMapping("/summary")
    public ResponseEntity<AccountsReceivableSummaryResponse> summary(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        return ResponseEntity.ok(summaryService.summary(effectiveShopNo, partnerCode, fromDate, toDate));
    }

    // -------- 再集計 --------

    @PostMapping("/aggregate")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<Map<String, Object>> aggregate(
            @Valid @RequestBody AccountsReceivableAggregateRequest request) {
        try {
            LocalDate.parse(request.getTargetDate(), YYYY_MM_DD);
        } catch (Exception e) {
            // SF-E08: 業務メッセージは IllegalArgumentException 経由で GlobalExceptionHandler に委譲し
            // 400 + body { message } で統一。
            throw new IllegalArgumentException("targetDate は yyyyMMdd 形式で指定してください");
        }
        // SF-E10: cutoffType をマジックリテラルから enum へ。
        CutoffType cutoffType = CutoffType.fromCode(request.getCutoffType());
        String cutoffTypeCode = cutoffType.getCode();

        String beanName = "accountsReceivableSummaryJob";
        if (!applicationContext.containsBean(beanName)) {
            // T5: 内部 Bean 不在 (config 異常) → FinanceInternalException で 422 + 汎用化。
            throw new FinanceInternalException("集計ジョブが見つかりません: " + beanName);
        }
        Job job = (Job) applicationContext.getBean(beanName);

        JobParametersBuilder params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("targetDate", request.getTargetDate())
                .addString("cutoffType", cutoffTypeCode);

        var jobParams = params.toJobParameters();
        batchExecutor.submit(() -> {
            try {
                jobLauncher.run(job, jobParams);
            } catch (Exception e) {
                log.error("売掛金集計ジョブの非同期実行に失敗: targetDate={}, cutoffType={}",
                        request.getTargetDate(), cutoffTypeCode, e);
            }
        });

        return ResponseEntity.accepted()
                .body(Map.of(
                        "status", "STARTED",
                        "targetDate", request.getTargetDate(),
                        "cutoffType", cutoffTypeCode));
    }

    // -------- 一括検証 --------

    @PostMapping("/bulk-verify")
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<AccountsReceivableBulkVerifyResponse> bulkVerify(
            @Valid @RequestBody AccountsReceivableBulkVerifyRequest request) {
        // SF-E06: 期間逆転は 400 で弾く。
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new IllegalArgumentException("fromDate は toDate 以前を指定してください");
        }
        // SF-E09: shopNo が指定されている場合のみアクセス権チェック (admin の null = 全店舗は許容)。
        if (request.getShopNo() != null) {
            assertShopAccess(request.getShopNo());
        }
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(request.getShopNo());

        // SF-E05: reconcile + reload + verify + saveAll を 1 tx に閉じ込める。
        AccountsReceivableBulkVerifyService.Result svcResult =
                bulkVerifyService.execute(effectiveShopNo, request.getFromDate(), request.getToDate());
        InvoiceVerificationSummary result = svcResult.getVerification();
        AccountsReceivableCutoffReconciler.ReconcileResult reconcile = svcResult.getReconcile();

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
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
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
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
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
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
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
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    public ResponseEntity<InputStreamResource> exportMfCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long initialTransactionNo) throws Exception {
        // SF-E08: 業務メッセージは IllegalArgumentException で 400 統一。
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate と toDate は必須です");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate は toDate 以前を指定してください");
        }
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByDateRangeAndMfExportEnabled(fromDate, toDate, true);
        log.info("売掛金→売上仕訳CSV DL: fromDate={}, toDate={}, 件数={}", fromDate, toDate, summaries.size());

        // SF-E01 (1): 0 件は abort。空 CSV を DL させて運用者が「出した気」になる事故を防ぐ。
        // T5: 業務メッセージなので FinanceBusinessException → 400 + 元メッセージで client へ。
        if (summaries.isEmpty()) {
            throw new FinanceBusinessException(
                    "指定期間に MF 出力対象の売掛金がありません");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, SalesJournalCsvService.CP932)) {
            salesJournalCsvService.writeCsv(summaries, w, initialTransactionNo);
        }

        // SF-E01 (2): CSV出力済みマーカーは saveAll (1 tx) に集約。
        salesJournalCsvService.markExported(summaries);
        summaryService.saveAll(summaries);

        String filename = generateFileName(fromDate, toDate);
        byte[] bytes = baos.toByteArray();
        // SF-E15: HTTP charset ヘッダを実体 (CP932) と揃える。
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=" + SalesJournalCsvService.CP932.name()))
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
            // ResponseStatusException(FORBIDDEN) は GlobalExceptionHandler が 403 で処理する。
            // 認可境界はあえて Spring 標準 ResponseStatusException を維持 (通常の業務エラーと区別)。
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
        }
    }
}
