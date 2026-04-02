package jp.co.oda32.api.purchase;

import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import jp.co.oda32.domain.service.purchase.QuoteImportService;
import jp.co.oda32.dto.purchase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/quote-imports")
@RequiredArgsConstructor
public class QuoteImportController {

    private final QuoteImportService quoteImportService;

    @GetMapping
    public ResponseEntity<List<QuoteImportHeaderResponse>> list() {
        List<TQuoteImportHeader> headers = quoteImportService.findAllHeaders();
        List<QuoteImportHeaderResponse> response = headers.stream()
                .map(h -> QuoteImportHeaderResponse.from(h, quoteImportService.getRemainingCount(h.getQuoteImportId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{importId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Integer importId) {
        TQuoteImportHeader header = quoteImportService.getHeader(importId);
        if (header == null) {
            return ResponseEntity.notFound().build();
        }
        List<TQuoteImportDetail> pendingDetails = quoteImportService.getPendingDetails(importId);
        List<TQuoteImportDetail> processedDetails = quoteImportService.getProcessedDetails(importId);
        int remaining = pendingDetails.size();

        Map<String, Object> result = new HashMap<>();
        result.put("header", QuoteImportHeaderResponse.from(header, remaining));
        result.put("details", pendingDetails.stream().map(QuoteImportDetailResponse::from).collect(Collectors.toList()));
        result.put("processedDetails", processedDetails.stream().map(QuoteImportDetailResponse::from).collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<QuoteImportHeaderResponse> create(
            @Valid @RequestBody QuoteImportCreateRequest request) {
        TQuoteImportHeader saved = quoteImportService.createImport(request);
        int remaining = quoteImportService.getRemainingCount(saved.getQuoteImportId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QuoteImportHeaderResponse.from(saved, remaining));
    }

    @PutMapping("/{importId}/supplier")
    public ResponseEntity<Void> matchSupplier(
            @PathVariable Integer importId,
            @Valid @RequestBody QuoteImportSupplierMatchRequest request) {
        quoteImportService.matchSupplier(importId, request.getSupplierCode(), request.getSupplierNo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{importId}/details/{detailId}/match")
    public ResponseEntity<Void> matchGoods(
            @PathVariable Integer importId,
            @PathVariable Integer detailId,
            @Valid @RequestBody QuoteImportMatchRequest request) throws Exception {
        quoteImportService.matchGoods(importId, detailId, request.getGoodsCode(), request.getGoodsNo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{importId}/details/{detailId}/create-new")
    public ResponseEntity<Void> createNew(
            @PathVariable Integer importId,
            @PathVariable Integer detailId,
            @Valid @RequestBody QuoteImportCreateNewRequest request) throws Exception {
        quoteImportService.createNewAndMatch(importId, detailId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{importId}/details/{detailId}")
    public ResponseEntity<Void> skipDetail(
            @PathVariable Integer importId,
            @PathVariable Integer detailId) {
        quoteImportService.skipDetail(detailId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{importId}/details/{detailId}/undo")
    public ResponseEntity<Void> undoDetail(
            @PathVariable Integer importId,
            @PathVariable Integer detailId) {
        quoteImportService.undoDetail(importId, detailId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{importId}")
    public ResponseEntity<Void> deleteImport(@PathVariable Integer importId) {
        quoteImportService.deleteImport(importId);
        return ResponseEntity.noContent().build();
    }
}
