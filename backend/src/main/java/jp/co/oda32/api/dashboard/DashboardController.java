package jp.co.oda32.api.dashboard;

import jp.co.oda32.domain.model.VSalesMonthlySummary;
import jp.co.oda32.domain.repository.VSalesMonthlySummaryRepository;
import jp.co.oda32.dto.dashboard.SalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final VSalesMonthlySummaryRepository vSalesMonthlySummaryRepository;

    @GetMapping("/sales-summary")
    public ResponseEntity<List<SalesSummaryResponse>> getSalesSummary() {
        List<VSalesMonthlySummary> summaries = vSalesMonthlySummaryRepository.findAll();
        List<SalesSummaryResponse> response = summaries.stream()
                .map(s -> SalesSummaryResponse.builder()
                        .shopNo(s.getShopNo())
                        .month(s.getMonth())
                        .totalSales(s.getSalesTotal())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
