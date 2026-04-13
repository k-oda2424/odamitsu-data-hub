package jp.co.oda32.api.stock;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.stock.TStock;
import jp.co.oda32.domain.model.stock.TStockLog;
import jp.co.oda32.domain.service.stock.TStockLogService;
import jp.co.oda32.domain.service.stock.TStockService;
import jp.co.oda32.dto.stock.StockCreateRequest;
import jp.co.oda32.dto.stock.StockLogResponse;
import jp.co.oda32.dto.stock.StockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/stock")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class StockController {

    private final TStockService tStockService;
    private final TStockLogService tStockLogService;

    @GetMapping
    public ResponseEntity<List<StockResponse>> list(
            @RequestParam(required = false) Integer goodsNo,
            @RequestParam(required = false) Integer warehouseNo,
            @RequestParam(required = false) String goodsName) {
        List<TStock> stocks = tStockService.find(goodsNo, null, warehouseNo, goodsName, Flag.NO);
        return ResponseEntity.ok(stocks.stream().map(StockResponse::from).collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<StockResponse> create(@Valid @RequestBody StockCreateRequest request) throws Exception {
        TStock stock = new TStock();
        stock.setGoodsNo(request.getGoodsNo());
        stock.setWarehouseNo(request.getWarehouseNo());
        stock.setCompanyNo(request.getCompanyNo());
        stock.setShopNo(request.getShopNo());
        stock.setUnit1No(request.getUnit1No());
        stock.setUnit1StockNum(request.getUnit1StockNum());
        stock.setUnit2No(request.getUnit2No());
        stock.setUnit2StockNum(request.getUnit2StockNum());
        stock.setUnit3No(request.getUnit3No());
        stock.setUnit3StockNum(request.getUnit3StockNum());
        stock.setLeadTime(request.getLeadTime());
        TStock saved = tStockService.insert(stock);
        return ResponseEntity.ok(StockResponse.from(saved));
    }

    @GetMapping("/log")
    public ResponseEntity<List<StockLogResponse>> logs(
            @RequestParam(required = false) Integer goodsNo,
            @RequestParam(required = false) Integer warehouseNo) {
        List<TStockLog> logs = tStockLogService.find(goodsNo, null, warehouseNo, Flag.NO);
        return ResponseEntity.ok(logs.stream().map(StockLogResponse::from).collect(Collectors.toList()));
    }
}
