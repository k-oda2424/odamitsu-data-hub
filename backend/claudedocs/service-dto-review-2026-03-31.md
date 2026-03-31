# Service & DTO Code Review Report - 2026-03-31

## Scope
- Service layer: `domain/service/goods/`, `domain/service/master/`, `CommonService`, `CustomService`
- DTO layer: `dto/goods/`, `dto/master/`
- Entity layer: `domain/model/goods/`, `domain/model/master/` (key entities)

---

## BLOCKER

### B-1: SQL Injection in MGoodsService.buildQuery()
- **File**: `domain/service/goods/MGoodsService.java`, lines 180-200
- **Description**: User input (`goodsName`, `keyword`, `janCode`) is directly concatenated into a native SQL query string without parameterization.
- **Code**:
  ```java
  querySB.append(" and g.goods_name like '%").append(mGoods.getGoodsName()).append("%'");
  ```
- **Impact**: Full SQL injection vulnerability. An attacker can execute arbitrary SQL.
- **Fix**: Use parameterized native query with `EntityManager.createNativeQuery()` + `setParameter()`, or replace with Specification-based query (which already exists as `findByNotExistWSalesGoods(String, String, String, Integer)` on line 76).

---

## CRITICAL

### C-1: No @Transactional on any write method across most services
- **Files**: All services extending `CustomService` (MGoodsService, MSalesGoodsService, WSalesGoodsService, MMakerService, MWarehouseService, etc.)
- **Description**: `insert()`, `update()`, and `delete()` methods have no `@Transactional` annotation. Spring Data `save()` creates its own transaction per call, but the service-level business logic (find + validate + save) is not atomic. If `validateUpdateByShop()` passes but the subsequent save fails, or if a service method calls multiple repository operations, there is no transactional guarantee.
- **Example**: `MGoodsService.update(GoodsModifyForm)` (line 116) does `findById()` then `save()` without a single transaction boundary.
- **Exception**: `MPartnerGoodsService.updateAllClearOrderNumPerYear()` (line 82) correctly has `@Transactional`. `MSupplierShopMappingService` also correctly uses `@Transactional`.
- **Fix**: Add `@Transactional` to all write methods, or add `@Transactional` at class level with `@Transactional(readOnly = true)` and override on write methods.

### C-2: No @Transactional(readOnly = true) on any read method
- **Files**: All services except `MSupplierShopMappingService`
- **Description**: Read-only methods (e.g., `findAll()`, `getByGoodsNo()`, `find()`) lack `@Transactional(readOnly = true)`. This misses Hibernate dirty-checking optimization and prevents proper read-replica routing if ever configured.
- **Fix**: Add `@Transactional(readOnly = true)` to read methods or at class level.

### C-3: Generic `throws Exception` used everywhere instead of domain-specific exceptions
- **Files**: All services extending `CustomService`; `CustomService.java` lines 29, 44, 59, 74, 78
- **Description**: Every write method throws raw `java.lang.Exception`. The `validateUpdateByShop()` method (line 78-84) throws `new Exception("...")`. `CommonService.getMakerMap()` (line 97) throws `new Exception("...")`.
- **Impact**: Callers cannot distinguish between business validation failures and system errors. Makes proper error handling at the controller/`@RestControllerAdvice` layer impossible.
- **Fix**: Create domain exceptions (e.g., `ShopAuthorizationException`, `ResourceNotFoundException`) extending `RuntimeException`.

### C-4: Silent swallowing of LoginUser exceptions in CustomService
- **File**: `domain/service/CustomService.java`, lines 30-36, 46-50, 62-66
- **Code**:
  ```java
  try {
      loginUser = LoginUserUtil.getLoginUserInfo();
      entity.setAddUserNo(loginUser.getUser().getLoginUserNo());
  } catch (Exception e) {
      entity.setAddUserNo(this.batchId);
  }
  ```
- **Description**: Any exception (NPE, ClassCast, etc.) is silently caught and treated as "batch mode". This masks real errors during web requests.
- **Fix**: Catch only the specific exception that indicates no authenticated user is present.

---

## MAJOR

### M-1: N+1 query risk in DTO mapping for GoodsDetailResponse and SalesGoodsDetailResponse
- **Files**: `dto/goods/GoodsDetailResponse.java` line 43, `dto/goods/SalesGoodsDetailResponse.java` lines 39-63, 68-93
- **Description**: The `from()` methods access lazy-loaded relationships: `goods.getMaker()`, `sg.getMGoods()`, `sg.getMSupplier()`. When mapping a list of entities, each entity triggers separate SQL queries for `maker`, `mGoods`, and `mSupplier`.
- **Impact**: For a list of N sales goods, this generates up to 3N additional queries.
- **Fix**: Use `@EntityGraph` or `JOIN FETCH` in repository queries that feed these DTOs. Alternatively, use `@BatchSize` on the entity relationships.

### M-2: N+1 query risk in GoodsResponse.from()
- **File**: `dto/goods/GoodsResponse.java` line 30
- **Code**: `goods.getMaker() != null ? goods.getMaker().getMakerName() : null`
- **Description**: Same issue as M-1 for the MGoods -> MMaker relationship when mapping lists.

### M-3: N+1 in CommonService.getPartnerMap()
- **File**: `domain/service/CommonService.java` line 131
- **Code**: `company.getPartner().getPartnerCode().length() < 10`
- **Description**: Iterates over company list and accesses lazy-loaded `partner` relationship for each entry.

### M-4: Duplicated DTO mapping logic in SalesGoodsDetailResponse
- **File**: `dto/goods/SalesGoodsDetailResponse.java`, lines 38-65 and 67-94
- **Description**: `from(MSalesGoods)` and `from(WSalesGoods)` are nearly identical (differ only in `isWork` value). Both `MSalesGoods` and `WSalesGoods` implement `ISalesGoods`, but the mapping doesn't use the interface.
- **Fix**: Create a single `from(ISalesGoods sg)` factory method that reads from the interface and calls `sg.getIsWork()`.

### M-5: WSalesGoodsService.update() applies name cleanup to wrong object
- **File**: `domain/service/goods/WSalesGoodsService.java`, lines 53-58
- **Code**:
  ```java
  WSalesGoods wSalesGoods = this.getByPK(iSalesGoods.getShopNo(), iSalesGoods.getGoodsNo());
  BeanUtils.copyProperties(iSalesGoods, wSalesGoods);
  String goodsName = GoodsUtil.removePriceFromName(iSalesGoods.getGoodsName());
  iSalesGoods.setGoodsName(goodsName);  // <-- sets on source, not on wSalesGoods
  return this.update(this.salesGoodsRepository, wSalesGoods);
  ```
- **Description**: After `BeanUtils.copyProperties` copies the original name to `wSalesGoods`, the cleaned name is set back on `iSalesGoods` (the input parameter), not on `wSalesGoods` (the entity being saved). The saved entity retains the uncleaned name with `@price` suffix.
- **Impact**: Data corruption - price text remains in goods names in the `w_sales_goods` table.
- **Fix**: Set the cleaned name on `wSalesGoods.setGoodsName(goodsName)` instead.

### M-6: WSalesGoodsService.insert() same bug as M-5
- **File**: `domain/service/goods/WSalesGoodsService.java`, lines 61-67
- **Description**: Same pattern - name cleanup applied to `iSalesGoods` instead of `wSalesGoods` after BeanUtils copy.

### M-7: MGoodsUnitService.delete() uses wrong ID for lookup
- **File**: `domain/service/goods/MGoodsUnitService.java`, line 103
- **Code**: `this.goodsUnitRepository.findById(goodsUnit.getGoodsNo()).orElseThrow()`
- **Description**: `findById()` expects the primary key (`goodsUnitNo`), but `goodsUnit.getGoodsNo()` is passed instead. This will find the wrong record or throw NoSuchElementException.
- **Fix**: Use `goodsUnit.getGoodsUnitNo()` (assuming that's the PK field name).

### M-8: In-memory filtering instead of database query in MGoodsUnitService.findByGoodsNo()
- **File**: `domain/service/goods/MGoodsUnitService.java`, lines 47-52
- **Description**: Fetches all goods units for a goodsNo from DB, then filters `delFlg` in Java. The `delFlg` check should be part of the repository query.
- **Fix**: Add a repository method `findByGoodsNoAndDelFlg()`.

### M-9: @EnableAutoConfiguration on MPartnerGoodsPriceChangePlanService
- **File**: `domain/service/goods/MPartnerGoodsPriceChangePlanService.java`, line 24
- **Description**: `@EnableAutoConfiguration` is misplaced on a service class. This annotation belongs on the main application class. While likely harmless, it causes redundant component scanning.
- **Fix**: Remove `@EnableAutoConfiguration`.

### M-10: MMaker.getShopNo() always returns null despite having shopNo field
- **File**: `domain/model/master/MMaker.java`, lines 33, 47-50
- **Description**: The entity has `private Integer shopNo` (line 33) but overrides `getShopNo()` to always return `null` (line 49). The `@Data` lombok annotation would generate `getShopNo()` returning the field, but the explicit override masks it. This breaks the `CustomService.validateUpdateByShop()` check -- shop-scoped validation is always bypassed for makers.
- **Fix**: Remove the explicit `getShopNo()` override and let Lombok generate it, OR if the intent is to bypass shop validation, document this clearly.

---

## MINOR

### m-1: Field injection via @Autowired constructor but non-final Specification fields
- **Files**: `MGoodsService.java` line 30, `MSalesGoodsService.java` line 26, `MMakerService.java` line 28, etc.
- **Description**: The `Specification` helper objects (e.g., `GoodsSpecification`, `MakerSpecification`) are instantiated as non-final instance fields with `new`. These are stateless and could be `static final` constants or injected beans.
- **Fix**: Make them `private static final` since they hold no state.

### m-2: entityManager field not final in MGoodsService
- **File**: `domain/service/goods/MGoodsService.java`, line 31
- **Description**: `private EntityManager entityManager` should be `private final EntityManager entityManager` to match immutability best practices with constructor injection.

### m-3: MakerModifyForm lacks @NotBlank on makerName
- **File**: `dto/master/MakerModifyForm.java`, line 19
- **Description**: `makerName` has no validation annotation, meaning a blank name could be submitted during update.
- **Fix**: Add `@NotBlank` to `makerName`.

### m-4: WarehouseCreateForm lacks validation annotations
- **File**: `dto/master/WarehouseCreateForm.java`
- **Description**: `warehouseName` has no `@NotBlank` validation.

### m-5: WarehouseModifyForm lacks validation on warehouseName
- **File**: `dto/master/WarehouseModifyForm.java`
- **Description**: Same as m-4.

### m-6: MSalesGoodsService.delete() variable named "updateMaker" for a sales goods entity
- **File**: `domain/service/goods/MSalesGoodsService.java`, line 73
- **Description**: Copy-paste artifact. Variable name is misleading.

### m-7: Inconsistent use of Collectors.toList() vs toList()
- **Files**: Multiple service files
- **Description**: Java 21 provides `Stream.toList()` directly. The codebase uses the verbose `Collectors.toList()` throughout.

### m-8: MSupplierShopMappingService.importFromCsv() hardcodes "UTF-8" string
- **File**: `domain/service/master/MSupplierShopMappingService.java`, line 85
- **Description**: Use `StandardCharsets.UTF_8` instead of the string literal "UTF-8" to avoid potential `UnsupportedEncodingException`.

### m-9: Mutable @Setter on CustomService.batchId
- **File**: `domain/service/CustomService.java`, line 20
- **Description**: `@Setter protected Integer batchId` makes this mutable state on a singleton bean, which is not thread-safe if multiple batch jobs run concurrently.
- **Fix**: Pass batchId as a method parameter or use a thread-local/request-scoped approach.

---

## Summary

| Severity | Count |
|----------|-------|
| Blocker  | 1     |
| Critical | 4     |
| Major    | 10    |
| Minor    | 9     |

### Priority actions:
1. **Immediate**: Fix SQL injection in `MGoodsService.buildQuery()` (B-1)
2. **High**: Fix name cleanup bug in `WSalesGoodsService` (M-5, M-6)
3. **High**: Fix wrong PK in `MGoodsUnitService.delete()` (M-7)
4. **High**: Add `@Transactional` annotations (C-1, C-2)
5. **High**: Replace `throws Exception` with domain exceptions (C-3)
