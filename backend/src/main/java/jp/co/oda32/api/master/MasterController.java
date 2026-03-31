package jp.co.oda32.api.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jp.co.oda32.domain.service.master.MMakerService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MShopService;
import jp.co.oda32.domain.service.master.MSupplierService;
import jp.co.oda32.domain.service.master.MWarehouseService;
import jp.co.oda32.domain.service.order.MDeliveryDestinationService;
import jp.co.oda32.dto.master.DeliveryDestinationResponse;
import jp.co.oda32.dto.master.MakerResponse;
import jp.co.oda32.dto.master.PartnerResponse;
import jp.co.oda32.dto.master.ShopResponse;
import jp.co.oda32.dto.master.SupplierResponse;
import jp.co.oda32.dto.master.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/masters")
@RequiredArgsConstructor
public class MasterController {

    private final MMakerService mMakerService;
    private final MShopService mShopService;
    private final MWarehouseService mWarehouseService;
    private final MSupplierService mSupplierService;
    private final MPartnerService mPartnerService;
    private final MDeliveryDestinationService mDeliveryDestinationService;

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

    @GetMapping("/suppliers")
    public ResponseEntity<List<SupplierResponse>> listSuppliers(
            @RequestParam(required = false) Integer shopNo) {
        List<MSupplier> suppliers = shopNo != null
                ? mSupplierService.findByShopNo(shopNo)
                : mSupplierService.findAll();
        return ResponseEntity.ok(suppliers.stream()
                .filter(s -> s.getSupplierName() != null && !s.getSupplierName().isEmpty())
                .filter(s -> s.getPaymentSupplierNo() != null)
                .map(SupplierResponse::from)
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

    @GetMapping("/destinations")
    public ResponseEntity<List<DeliveryDestinationResponse>> listDestinations(
            @RequestParam Integer partnerNo) {
        List<MDeliveryDestination> destinations = mDeliveryDestinationService.findByPartnerNo(partnerNo);
        return ResponseEntity.ok(destinations.stream()
                .filter(d -> d.getDelFlg() == null || "0".equals(d.getDelFlg()))
                .map(DeliveryDestinationResponse::from)
                .collect(Collectors.toList()));
    }
}
