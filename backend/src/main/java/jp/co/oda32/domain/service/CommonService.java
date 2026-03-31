package jp.co.oda32.domain.service;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.*;
import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jp.co.oda32.domain.service.master.*;
import jp.co.oda32.domain.service.order.MDeliveryDestinationService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonService {
    @NonNull
    protected final MCompanyService companyService;
    @NonNull
    protected final MWarehouseService mWarehouseService;
    @NonNull
    protected final MShopService shopService;
    @NonNull
    protected final MSupplierService supplierService;
    @NonNull
    protected final MMakerService mMakerService;
    @NonNull
    protected final MPartnerService mPartnerService;
    @NonNull
    protected final MDeliveryDestinationService mDeliveryDestinationService;
    @NonNull
    protected final MTaxRateService mTaxRateService;

    public Map<Integer, String> getWarehouseMap(Integer companyNo) {
        List<MWarehouse> warehouseList = this.mWarehouseService.find(null, null, companyNo, Flag.NO);
        return warehouseList.stream()
                .collect(Collectors.toMap(MWarehouse::getWarehouseNo, MWarehouse::getWarehouseName));
    }

    public Map<Integer, String> getWarehouseMapByShopNo(Integer shopNo) {
        MCompany company = this.companyService.getByShopNo(shopNo);
        return this.getWarehouseMap(company.getCompanyNo());
    }

    public Map<Integer, String> getDestinationMap(Integer partnerNo) {
        List<MDeliveryDestination> deliveryDestinationList = this.mDeliveryDestinationService.findByPartnerNo(partnerNo);
        return deliveryDestinationList.stream()
                .collect(Collectors.toMap(MDeliveryDestination::getDestinationNo, MDeliveryDestination::getDestinationName));
    }

    public MCompany getMCompanyByPartnerNo(int partnerNo) {
        MPartner mPartner = this.mPartnerService.getByPartnerNo(partnerNo);
        if (mPartner != null) {
            return mPartner.getCompany();
        }
        return null;
    }

    public Map<Integer, String> getCompanyMap() {
        List<MCompany> companyList = this.companyService.findAll();
        if (CollectionUtil.isEmpty(companyList)) {
            log.warn("会社情報検索失敗");
            return null;
        }
        return companyList.stream()
                .sorted(Comparator.comparing(MCompany::getCompanyNo))
                .collect(Collectors.toMap(MCompany::getCompanyNo, MCompany::getCompanyName, (v1, v2) -> v1));
    }

    public Map<Integer, String> getShopMap() {
        List<MShop> shopList = this.shopService.findAll();
        if (CollectionUtil.isEmpty(shopList)) {
            log.warn("ショップ情報検索失敗");
            return null;
        }
        return shopList.stream()
                .sorted(Comparator.comparing(MShop::getShopNo))
                .collect(Collectors.toMap(MShop::getShopNo, MShop::getShopName, (v1, v2) -> v1));
    }

    public MShop getShop(Integer shopNo) {
        return this.shopService.getByShopNo(shopNo);
    }

    public Map<Integer, String> getMakerMap() throws Exception {
        List<MMaker> makerList = this.mMakerService.findAll();
        if (CollectionUtil.isEmpty(makerList)) {
            log.error("メーカー情報検索失敗");
            throw new Exception("メーカー情報検索失敗");
        }
        return makerList.stream()
                .collect(Collectors.toMap(MMaker::getMakerNo, MMaker::getMakerName, (v1, v2) -> v1));
    }

    public MSupplier getSupplier(Integer supplierNo) {
        return this.supplierService.getBySupplierNo(supplierNo);
    }

    public MWarehouse getWarehouse(Integer warehouseNo) {
        return this.mWarehouseService.getByWarehouseNo(warehouseNo);
    }

    public Map<Integer, String> getSupplierMap(Integer shopNo) {
        List<MSupplier> supplierList = this.supplierService.findByShopNo(shopNo);
        if (CollectionUtil.isEmpty(supplierList)) {
            log.warn("仕入先情報検索失敗");
            return null;
        }
        return supplierList.stream()
                .filter(supplier -> !StringUtil.isEmpty(supplier.getSupplierName()))
                .filter(supplier -> supplier.getPaymentSupplierNo() != null)
                .sorted(Comparator.comparing(MSupplier::getSupplierNo))
                .collect(Collectors.toMap(MSupplier::getSupplierNo, MSupplier::getSupplierName, (v1, v2) -> v1));
    }

    public Map<Integer, String> getPartnerMap(Integer shopNo) {
        List<MCompany> companyList = this.companyService.findByShopNoAndCompanyType(shopNo, CompanyType.PARTNER.getValue());
        if (CollectionUtil.isEmpty(companyList)) {
            log.warn("会社(得意先)情報検索失敗");
            return null;
        }
        return companyList.stream()
                .filter(company -> company.getPartner().getPartnerCode().length() < 10)
                .filter(company -> !StringUtil.isEqual(company.getPartner().getPartnerCode(), "999999"))
                .filter(company -> !StringUtil.isEmpty(company.getCompanyName()))
                .sorted(Comparator.comparing(MCompany::getCompanyName))
                .collect(Collectors.toMap(MCompany::getCompanyNo, MCompany::getCompanyName, (v1, v2) -> v1));
    }

    public MPartner getMPartner(int partnerNo) {
        return this.mPartnerService.getByPartnerNo(partnerNo);
    }

    public MDeliveryDestination getDestination(int destinationNo) {
        return this.mDeliveryDestinationService.getByPK(destinationNo);
    }

    public MTaxRate getMTaxRate() throws Exception {
        return this.mTaxRateService.getTaxRate();
    }
}
