package jp.co.oda32.api.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jakarta.validation.Valid;
import jp.co.oda32.domain.service.master.MMakerService;
import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.domain.service.master.MWarehouseService;
import jp.co.oda32.domain.service.order.MDeliveryDestinationService;
import jp.co.oda32.dto.master.DeliveryDestinationResponse;
import jp.co.oda32.dto.master.MakerCreateRequest;
import jp.co.oda32.dto.master.MakerResponse;
import jp.co.oda32.dto.master.PartnerCreateRequest;
import jp.co.oda32.dto.master.PartnerResponse;
import jp.co.oda32.dto.master.PaymentSupplierResponse;
import jp.co.oda32.dto.master.ShopLinkedFileResponse;
import jp.co.oda32.dto.master.ShopLinkedFileUpdateRequest;
import jp.co.oda32.dto.master.ShopResponse;
import jp.co.oda32.dto.master.SupplierCreateRequest;
import jp.co.oda32.dto.master.SupplierResponse;
import jp.co.oda32.dto.master.WarehouseCreateRequest;
import jp.co.oda32.dto.master.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/masters")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MasterController {

    private final MMakerService mMakerService;
    private final MShopService mShopService;
    private final MWarehouseService mWarehouseService;
    private final MSupplierService mSupplierService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final MPartnerService mPartnerService;
    private final MDeliveryDestinationService mDeliveryDestinationService;
    private final MShopLinkedFileService mShopLinkedFileService;

    @GetMapping("/shops")
    public ResponseEntity<List<ShopResponse>> listShops() {
        List<MShop> shops = mShopService.findAll();
        return ResponseEntity.ok(shops.stream()
                .filter(s -> s.getDelFlg() == null || "0".equals(s.getDelFlg()))
                .map(ShopResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/makers")
    public ResponseEntity<List<MakerResponse>> listMakers() {
        List<MMaker> makers = mMakerService.findAll();
        return ResponseEntity.ok(makers.stream().map(MakerResponse::from).collect(Collectors.toList()));
    }

    /**
     * 仕入先一覧（個別 m_supplier レコード）。個別の仕入先選択用ドロップダウンに使用。
     * グループ単位（支払先）で選びたい場合は {@link #listPaymentSuppliers} を使う。
     * 削除済み・名称未設定のレコードは除外する。
     */
    @GetMapping("/suppliers")
    public ResponseEntity<List<SupplierResponse>> listSuppliers(
            @RequestParam(required = false) Integer shopNo) {
        List<MSupplier> suppliers = shopNo != null
                ? mSupplierService.findByShopNo(shopNo)
                : mSupplierService.findAll();
        return ResponseEntity.ok(suppliers.stream()
                .filter(s -> s.getSupplierName() != null && !s.getSupplierName().isEmpty())
                .filter(s -> s.getDelFlg() == null || "0".equals(s.getDelFlg()))
                .map(SupplierResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 支払先一覧（グループ単位の仕入先）。比較見積等のドロップダウン用。
     */
    @GetMapping("/payment-suppliers")
    public ResponseEntity<List<PaymentSupplierResponse>> listPaymentSuppliers(
            @RequestParam(required = false) Integer shopNo) {
        List<MPaymentSupplier> list = mPaymentSupplierService.findByShopNo(shopNo);
        return ResponseEntity.ok(list.stream()
                .filter(p -> p.getPaymentSupplierName() != null && !p.getPaymentSupplierName().isEmpty())
                .map(PaymentSupplierResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseResponse>> listWarehouses(
            @RequestParam(required = false) Integer companyNo) {
        List<MWarehouse> warehouses = mWarehouseService.find(null, null, companyNo, null);
        return ResponseEntity.ok(warehouses.stream().map(WarehouseResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/partners")
    public ResponseEntity<List<PartnerResponse>> listPartners(
            @RequestParam Integer shopNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String partnerName) {
        List<MPartner> partners = mPartnerService.findByShopNo(shopNo, partnerName, partnerCode);
        return ResponseEntity.ok(partners.stream()
                .map(PartnerResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/shop-linked-files")
    public ResponseEntity<List<ShopLinkedFileResponse>> listShopLinkedFiles() {
        List<MShopLinkedFile> files = mShopLinkedFileService.findAll();
        return ResponseEntity.ok(files.stream()
                .map(ShopLinkedFileResponse::from)
                .collect(Collectors.toList()));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/shop-linked-files/{shopNo}")
    public ResponseEntity<ShopLinkedFileResponse> updateShopLinkedFile(
            @PathVariable Integer shopNo,
            @Valid @RequestBody ShopLinkedFileUpdateRequest request) throws Exception {
        MShopLinkedFile file = mShopLinkedFileService.getByShopNo(shopNo);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        file.setSmileOrderInputFileName(request.getSmileOrderInputFileName());
        file.setSmilePurchaseFileName(request.getSmilePurchaseFileName());
        file.setSmileOrderOutputFileName(request.getSmileOrderOutputFileName());
        file.setSmilePartnerOutputFileName(request.getSmilePartnerOutputFileName());
        file.setSmileDestinationOutputFileName(request.getSmileDestinationOutputFileName());
        file.setSmileGoodsImportFileName(request.getSmileGoodsImportFileName());
        file.setBCartLogisticsImportFileName(request.getBCartLogisticsImportFileName());
        file.setInvoiceFilePath(request.getInvoiceFilePath());
        mShopLinkedFileService.update(file);
        return ResponseEntity.ok(ShopLinkedFileResponse.from(file));
    }

    @GetMapping("/destinations")
    public ResponseEntity<List<DeliveryDestinationResponse>> listDestinations(
            @RequestParam Integer partnerNo) {
        List<MDeliveryDestination> destinations = mDeliveryDestinationService.findByPartnerNo(partnerNo);
        return ResponseEntity.ok(destinations.stream()
                .filter(d -> d.getDelFlg() == null || "0".equals(d.getDelFlg()))
                .map(DeliveryDestinationResponse::from)
                .collect(Collectors.toList()));
    }

    // ===== Maker CRUD =====

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/makers")
    public ResponseEntity<MakerResponse> createMaker(@Valid @RequestBody MakerCreateRequest request) throws Exception {
        MMaker maker = new MMaker();
        maker.setMakerName(request.getMakerName());
        maker.setShopNo(0);
        MMaker saved = mMakerService.insert(maker);
        return ResponseEntity.ok(MakerResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/makers/{makerNo}")
    public ResponseEntity<MakerResponse> updateMaker(
            @PathVariable Integer makerNo, @Valid @RequestBody MakerCreateRequest request) throws Exception {
        MMaker maker = mMakerService.getByMakerNo(makerNo);
        if (maker == null) return ResponseEntity.notFound().build();
        maker.setMakerName(request.getMakerName());
        MMaker saved = mMakerService.update(maker);
        return ResponseEntity.ok(MakerResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/makers/{makerNo}")
    public ResponseEntity<Void> deleteMaker(@PathVariable Integer makerNo) throws Exception {
        MMaker maker = mMakerService.getByMakerNo(makerNo);
        if (maker == null) return ResponseEntity.notFound().build();
        mMakerService.delete(maker);
        return ResponseEntity.noContent().build();
    }

    // ===== Warehouse CRUD =====

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/warehouses")
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseCreateRequest request) throws Exception {
        MWarehouse warehouse = new MWarehouse();
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setCompanyNo(request.getCompanyNo());
        MWarehouse saved = mWarehouseService.insert(warehouse);
        return ResponseEntity.ok(WarehouseResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/warehouses/{warehouseNo}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable Integer warehouseNo, @Valid @RequestBody WarehouseCreateRequest request) throws Exception {
        MWarehouse warehouse = mWarehouseService.getByWarehouseNo(warehouseNo);
        if (warehouse == null) return ResponseEntity.notFound().build();
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setCompanyNo(request.getCompanyNo());
        MWarehouse saved = mWarehouseService.update(warehouse);
        return ResponseEntity.ok(WarehouseResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/warehouses/{warehouseNo}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Integer warehouseNo) throws Exception {
        MWarehouse warehouse = mWarehouseService.getByWarehouseNo(warehouseNo);
        if (warehouse == null) return ResponseEntity.notFound().build();
        mWarehouseService.delete(warehouse);
        return ResponseEntity.noContent().build();
    }

    // ===== Supplier CRUD =====

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/suppliers")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest request) throws Exception {
        MSupplier supplier = new MSupplier();
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setSupplierNameDisplay(request.getSupplierNameDisplay());
        supplier.setShopNo(request.getShopNo());
        supplier.setStandardLeadTime(request.getStandardLeadTime());
        MSupplier saved = mSupplierService.insert(supplier);
        return ResponseEntity.ok(SupplierResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/suppliers/{supplierNo}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Integer supplierNo, @Valid @RequestBody SupplierCreateRequest request) throws Exception {
        MSupplier supplier = mSupplierService.getBySupplierNo(supplierNo);
        if (supplier == null) return ResponseEntity.notFound().build();
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setSupplierNameDisplay(request.getSupplierNameDisplay());
        supplier.setStandardLeadTime(request.getStandardLeadTime());
        MSupplier saved = mSupplierService.update(supplier);
        return ResponseEntity.ok(SupplierResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/suppliers/{supplierNo}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Integer supplierNo) throws Exception {
        MSupplier supplier = mSupplierService.getBySupplierNo(supplierNo);
        if (supplier == null) return ResponseEntity.notFound().build();
        mSupplierService.delete(supplier);
        return ResponseEntity.noContent().build();
    }

    // ===== Partner CRUD =====

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/partners")
    public ResponseEntity<PartnerResponse> createPartner(@Valid @RequestBody PartnerCreateRequest request) throws Exception {
        MPartner partner = new MPartner();
        partner.setPartnerCode(request.getPartnerCode());
        partner.setPartnerName(request.getPartnerName());
        partner.setShopNo(request.getShopNo());
        partner.setAbbreviatedPartnerName(request.getAbbreviatedPartnerName());
        partner.setNote(request.getNote());
        MPartner saved = mPartnerService.insert(partner);
        return ResponseEntity.ok(PartnerResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/partners/{partnerNo}")
    public ResponseEntity<PartnerResponse> updatePartner(
            @PathVariable Integer partnerNo, @Valid @RequestBody PartnerCreateRequest request) throws Exception {
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        if (partner == null) return ResponseEntity.notFound().build();
        partner.setPartnerCode(request.getPartnerCode());
        partner.setPartnerName(request.getPartnerName());
        partner.setAbbreviatedPartnerName(request.getAbbreviatedPartnerName());
        partner.setNote(request.getNote());
        MPartner saved = mPartnerService.update(partner);
        return ResponseEntity.ok(PartnerResponse.from(saved));
    }

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @DeleteMapping("/partners/{partnerNo}")
    public ResponseEntity<Void> deletePartner(@PathVariable Integer partnerNo) throws Exception {
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        if (partner == null) return ResponseEntity.notFound().build();
        mPartnerService.delete(partner);
        return ResponseEntity.noContent().build();
    }
}
