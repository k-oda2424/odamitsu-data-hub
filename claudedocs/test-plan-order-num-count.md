# Test Plan: OrderNumCountTasklet - lastSalesDate Update

## Scope

Add `lastSalesDate` update logic to `OrderNumCountTasklet` and re-enable `orderNumCountStep` in `SmileOrderFileImportConfig`.

Backend-only change. No frontend impact.

## Test Approach

**Integration test** using `@SpringBootTest` with H2 in-memory database (PostgreSQL mode).

The tasklet depends on DB state across multiple tables (`m_partner_goods`, `t_order`, `t_order_detail`, `m_partner`), so a unit test with mocks would be fragile and miss real query behavior. An integration test with `@SpringBootTest` + H2 gives confidence that the actual JPA queries and entity updates work correctly.

### Test Class

`backend/src/test/java/jp/co/oda32/batch/order/OrderNumCountTaskletTest.java`

### Test Configuration

- `@SpringBootTest` (full context, not the excluded `TestApplication`)
- `@ActiveProfiles("test")` to use H2 (from `application-test.yml`)
- `spring.batch.job.enabled=false` already set in test profile (prevents auto-run)
- Use `@Autowired` for the tasklet, repositories, and `JobLauncherTestUtils` if testing the step within a job
- `@Transactional` + `@Rollback` on each test for isolation

### Alternative: Lightweight approach with mocks

If full integration is too slow or complex to set up (due to entity dependencies), use `@ExtendWith(MockitoExtension.class)` with mocked services. This is less ideal but acceptable as a fallback.

## Test Scenarios

### 1. Updates lastSalesDate with max order date

**Setup:**
- Insert `m_partner` (partnerNo=1, companyNo=1, shopNo=1)
- Insert `m_partner_goods` (partnerNo=1, goodsNo=100, destinationNo=1, lastSalesDate=null)
- Insert `t_order` (orderNo=1, orderDateTime=2025-10-15, shopNo=1, companyNo=1, partnerNo=1, destinationNo=1)
- Insert `t_order` (orderNo=2, orderDateTime=2026-02-20, shopNo=1, companyNo=1, partnerNo=1, destinationNo=1)
- Insert `t_order_detail` for each order (goodsNo=100)

**Execute:** `orderNumCountTasklet.execute(stepContribution, chunkContext)`

**Assert:**
- `m_partner_goods` (partnerNo=1, goodsNo=100, destinationNo=1) has `lastSalesDate = 2026-02-20`
- `orderNumPerYear` is updated with correct aggregated count

### 2. Clears orderNumPerYear before processing

**Setup:**
- Insert `m_partner_goods` with `orderNumPerYear = 50`
- No matching orders in the past year

**Execute:** tasklet

**Assert:**
- `orderNumPerYear = 0` (cleared by `updateAllClearOrderNumPerYear`)
- `lastSalesDate` unchanged (no orders to derive a date from)

### 3. Partner goods with no orders - lastSalesDate unchanged

**Setup:**
- Insert `m_partner_goods` (partnerNo=1, goodsNo=200, destinationNo=1, lastSalesDate=2025-06-01)
- No `t_order_detail` records for goodsNo=200 in the past year

**Execute:** tasklet

**Assert:**
- `lastSalesDate` remains `2025-06-01` (not overwritten to null)
- `orderNumPerYear = 0`

### 4. SmileOrderFileImportConfig includes orderNumCountStep

**Setup:** Load application context

**Assert:**
- `smileOrderFileImportJob` bean exists
- Job steps include `orderNumCountStep` (verify via `job.getStepNames()` or by inspecting the flow)
- `orderNumCountStep` is positioned correctly in the step chain (after `smileOrderFileImportStep`, before or after other steps as designed)

## Key Implementation Notes

### lastSalesDate calculation

In `partnerOrderProcess`, after grouping order details by `goodsNo_destinationNo`, also compute the max `orderDateTime` (from `TOrder` via `TOrderDetail.getTOrder()`) per group. Set this as `lastSalesDate` on the `MPartnerGoods` entity in `updatePartnerGoods`.

```java
// In partnerOrderProcess - compute max order date per goods/destination
Map<String, LocalDate> maxOrderDateMap = orderDetailList.stream()
    .collect(Collectors.groupingBy(
        od -> String.format("%d_%d", od.getGoodsNo(), od.getTOrder().getDestinationNo()),
        Collectors.mapping(
            od -> od.getTOrder().getOrderDateTime().toLocalDate(),
            Collectors.collectingAndThen(Collectors.maxBy(Comparator.naturalOrder()), opt -> opt.orElse(null))
        )
    ));

// In updatePartnerGoods - set lastSalesDate
if (maxOrderDate != null) {
    mPartnerGoods.setLastSalesDate(maxOrderDate);
}
```

### Important: Do NOT clear lastSalesDate

The `updateAllClearOrderNumPerYear` should only clear `orderNumPerYear`, not `lastSalesDate`. The `lastSalesDate` should only be updated when there are actual orders -- never nulled out.

## Test Data Dependencies

| Table | Required Fields |
|-------|----------------|
| `m_partner` | partnerNo, companyNo, shopNo, delFlg='0', addDateTime within 1 year |
| `m_partner_goods` | partnerNo, goodsNo, destinationNo, orderNumPerYear, lastSalesDate, delFlg='0' |
| `t_order` | orderNo, shopNo, companyNo, orderDateTime, destinationNo, partnerNo, delFlg='0' |
| `t_order_detail` | orderNo, orderDetailNo, shopNo, companyNo, goodsNo, orderNum, cancelNum, returnNum |

## Execution

```bash
cd backend && ./gradlew test --tests "jp.co.oda32.batch.order.OrderNumCountTaskletTest"
```
