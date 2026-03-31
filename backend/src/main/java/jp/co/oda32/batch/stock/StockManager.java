package jp.co.oda32.batch.stock;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.StockLogReason;
import jp.co.oda32.constant.UnitType;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.stock.IStockEntity;
import jp.co.oda32.domain.model.stock.TStock;
import jp.co.oda32.domain.model.stock.TStockLog;
import jp.co.oda32.domain.service.goods.MGoodsService;
import jp.co.oda32.domain.service.goods.MGoodsUnitService;
import jp.co.oda32.domain.service.master.MCompanyService;
import jp.co.oda32.domain.service.stock.TStockLogService;
import jp.co.oda32.domain.service.stock.TStockService;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 在庫を管理するクラス
 *
 * @author k_oda
 * @since 2019/07/09
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class StockManager {
    @NonNull
    private MGoodsUnitService goodsUnitService;
    @NonNull
    private TStockService tStockService;
    @NonNull
    private TStockLogService tStockLogService;
    @NonNull
    private MCompanyService mCompanyService;
    @NonNull
    private MGoodsService mGoodsService;

    // goods_noに紐づく商品単位リストのマップ
    private Map<Integer, List<MGoodsUnit>> goodsUnitMap = new HashMap<>();
    // goods_noに紐づく商品マスタ
    private Map<Integer, MGoods> mGoodsMap = new HashMap<>();
    // unit_noとcontain_numのマップ
    private Map<Integer, BigDecimal> unitNoMap = new HashMap<>();

    public List<TStock> findStock(int goodsNo, int companyNo) {
        return this.tStockService.find(goodsNo, companyNo, null, null, Flag.NO);
    }

    /**
     * 在庫移動処理
     *
     * @param companyNo   会社番号
     * @param goodsNo     商品番号
     * @param warehouseNo 倉庫番号
     * @param moveTime    在庫移動時刻
     * @param reason      在庫移動理由
     * @param moveNum     減らす場合は負を渡してください
     * @throws Exception 例外発生時
     */
    public void move(Integer companyNo, Integer goodsNo, Integer warehouseNo, LocalDateTime moveTime, StockLogReason reason, BigDecimal moveNum, boolean overWriteFlg) throws Exception {
        this.move(companyNo, goodsNo, warehouseNo, moveTime, reason, moveNum, null, null, null, overWriteFlg);
    }

    /**
     * 在庫移動処理
     *
     * @param companyNo              会社番号
     * @param goodsNo                商品番号
     * @param warehouseNo            倉庫番号
     * @param moveTime               在庫移動時刻
     * @param reason                 在庫移動理由
     * @param moveNum                減らす場合は負を渡してください
     * @param deliveryNo             出荷番号
     * @param destinationWarehouseNo 出荷先倉庫番号
     * @throws Exception 例外発生時
     */
    public TStock move(Integer companyNo, Integer goodsNo, Integer warehouseNo, LocalDateTime moveTime, StockLogReason reason,
                       BigDecimal moveNum, Integer deliveryNo, Integer destinationWarehouseNo, Integer purchaseNo, boolean overWriteFlg) throws Exception {
        // 移動数量が0の場合、登録しない
        if ((BigDecimal.ZERO.compareTo(moveNum) == 0 || goodsNo == null) && !overWriteFlg) {
            return null;
        }
        // 商品単位を検索
        List<MGoodsUnit> goodsUnitList;
        if (this.goodsUnitMap.containsKey(goodsNo)) {
            goodsUnitList = goodsUnitMap.get(goodsNo);
        } else {
            goodsUnitList = this.goodsUnitService.findByGoodsNo(goodsNo);
            this.goodsUnitMap.put(goodsNo, goodsUnitList);
            this.unitNoMap.putAll(goodsUnitList.stream().collect(Collectors.toMap(MGoodsUnit::getUnitNo, MGoodsUnit::getContainNum)));
        }
        if (CollectionUtil.isEmpty(goodsUnitList)) {
            // 商品単位の登録なし
            log.warn(String.format("商品単位の登録がありません。商品登録してください。商品番号:%d", goodsNo));
            return null;
        }
        MCompany company = this.mCompanyService.getByCompanyNo(companyNo);
        Integer shopNo = company.getCompanyType().equals(CompanyType.SHOP.getValue()) ? company.getShopNo() : null;
        TStockLog stockLog = TStockLog.builder()
                .companyNo(companyNo)
                .goodsNo(goodsNo)
                .warehouseNo(warehouseNo)
                .moveTime(moveTime)
                .reason(reason.getValue())
                .deliveryNo(deliveryNo)
                .destinationWarehouseNo(destinationWarehouseNo)
                .purchaseNo(purchaseNo)
                .shopNo(shopNo)
                .build();
        this.calculateGoodsUnit(stockLog, goodsUnitList, moveNum);
        // 在庫履歴登録
        this.tStockLogService.save(stockLog);
        MGoods mGoods;
        if (this.mGoodsMap.containsKey(goodsNo)) {
            mGoods = this.mGoodsMap.get(goodsNo);
        } else {
            mGoods = this.mGoodsService.getByGoodsNo(goodsNo);
            this.mGoodsMap.put(goodsNo, mGoods);
        }
        // 在庫の更新
        log.info(String.format("在庫に変動がありました。倉庫:%d 商品番号:%d 商品名:%s 数量:%s 理由:%s,時刻:%s", warehouseNo, goodsNo, mGoods.getGoodsName(), moveNum, reason.getValue(), DateTimeUtil.localDateTimeToDateTimeStr(moveTime)));
        return this.tStockProcess(stockLog, overWriteFlg);
    }

    private TStock tStockProcess(TStockLog stockLog, boolean overWriteFlg) throws Exception {
        TStock tStock = this.tStockService.getByPK(stockLog.getGoodsNo(), stockLog.getWarehouseNo());
        if (tStock == null) {
            return this.notExistTStock(stockLog);
        }
        return this.existTStock(tStock, stockLog, overWriteFlg);
    }

    /**
     * 在庫の登録が既にある場合の在庫加算
     *
     * @param stock    在庫
     * @param stockLog 在庫履歴
     * @throws Exception 例外
     */
    private TStock existTStock(TStock stock, TStockLog stockLog, boolean overWriteFlg) throws Exception {
        if (!stock.getGoodsNo().equals(stockLog.getGoodsNo())) {
            throw new Exception(String.format("在庫加算する商品番号が異なります。stock:%d stock_log:%d", stock.getGoodsNo(), stockLog.getGoodsNo()));
        }
        BigDecimal newStockNum;
        // 在庫変動数(バラ)
        BigDecimal moveStockNum = this.calculateMinGoodsUnitNum(stockLog);
        if (!overWriteFlg) {
            // 在庫の加算
            // 現在の在庫数(バラ)
            BigDecimal nowStockNum = this.calculateMinGoodsUnitNum(stock);
            newStockNum = nowStockNum.add(moveStockNum);
        } else {
            newStockNum = moveStockNum;
        }
        stock = this.calculateGoodsUnit(stock, newStockNum);
        return this.tStockService.update(stock);
    }

    /**
     * 在庫登録がない場合の在庫登録処理
     *
     * @param stockLog 在庫履歴
     * @throws Exception 例外
     */
    private TStock notExistTStock(TStockLog stockLog) throws Exception {
        // stockLogをtStockにコピー
        TStock stock = new TStock();
        BeanUtils.copyProperties(stockLog, stock);
        return this.tStockService.insert(stock);
    }

    /**
     * 在庫の商品単位(ケース、バラ)を計算し、各カラムに設定します。
     *
     * @param stock    在庫
     * @param pieceNum バラの数
     * @return 在庫数を設定した在庫
     * @throws Exception 例外
     */
    private TStock calculateGoodsUnit(TStock stock, BigDecimal pieceNum) throws Exception {
        if (this.goodsUnitMap.containsKey(stock.getGoodsNo())) {
            return (TStock) this.calculateGoodsUnit(stock, this.goodsUnitMap.get(stock.getGoodsNo()), pieceNum);
        }
        List<MGoodsUnit> goodsUnitList = this.goodsUnitService.findByGoodsNo(stock.getGoodsNo());
        this.goodsUnitMap.put(stock.getGoodsNo(), goodsUnitList);
        this.unitNoMap.putAll(goodsUnitList.stream().collect(Collectors.toMap(MGoodsUnit::getUnitNo, MGoodsUnit::getContainNum)));
        return (TStock) this.calculateGoodsUnit(stock, goodsUnitList, pieceNum);
    }

    /**
     * 在庫の商品単位を計算し、在庫の商品単位、在庫数を設定します。
     *
     * @param iStockEntity  在庫
     * @param goodsUnitList 商品単位リスト
     * @param moveNum       在庫数
     * @return 在庫数を設定した在庫
     * @throws Exception 例外
     */
    private IStockEntity calculateGoodsUnit(IStockEntity iStockEntity, List<MGoodsUnit> goodsUnitList, BigDecimal moveNum) throws Exception {
        // goodsUnitの内、親がいないのが最小単位
        MGoodsUnit minGoodsUnit = goodsUnitList.stream()
                .filter(mGoodsUnit -> UnitType.PIECE.getValue().equals(mGoodsUnit.getUnit()))
                .findFirst().orElseThrow(() -> new Exception(String.format("最小単位を見つけられません。goodsNo:%d", iStockEntity.getGoodsNo())));
        iStockEntity.setUnit1No(minGoodsUnit.getUnitNo());

        MGoodsUnit goodsUnit2 = goodsUnitList.stream()
                .filter(mGoodsUnit -> mGoodsUnit.getUnitNo().equals(minGoodsUnit.getParentUnitNo()))
                .filter(mGoodsUnit -> BigDecimal.ONE.compareTo(mGoodsUnit.getContainNum()) < 0)
                .findFirst().orElse(null);
        MGoodsUnit goodsUnit3 = null;

        if (goodsUnit2 != null) {
            iStockEntity.setUnit2No(goodsUnit2.getUnitNo());
            goodsUnit3 = goodsUnitList.stream()
                    .filter(mGoodsUnit -> mGoodsUnit.getUnitNo().equals(goodsUnit2.getParentUnitNo()))
                    .findFirst().orElse(null);
        } else {
            // 最小単位(バラ)での登録のみ
            iStockEntity.setUnit1StockNum(moveNum);
            return iStockEntity;
        }

        // バラとケースの計算
        BigDecimal[] unit2Division = moveNum.divideAndRemainder(goodsUnit2.getContainNum());
        BigDecimal unit2Num = unit2Division[0];
        BigDecimal unit1Num = unit2Division[1];

        if (goodsUnit3 == null) {
            iStockEntity.setUnit1StockNum(unit1Num);
            iStockEntity.setUnit2StockNum(unit2Num);
            return iStockEntity;
        }

        iStockEntity.setUnit3No(goodsUnit3.getUnitNo());
        BigDecimal[] unit3Division = unit2Num.divideAndRemainder(goodsUnit3.getContainNum());
        BigDecimal unit3Num = unit3Division[0];
        unit2Num = unit3Division[1];

        iStockEntity.setUnit1StockNum(unit1Num);
        iStockEntity.setUnit2StockNum(unit2Num);
        iStockEntity.setUnit3StockNum(unit3Num);
        return iStockEntity;
    }

    /**
     * 在庫を強制的に上書きする処理
     * 棚卸しなど
     *
     * @param goodsNo     商品番号
     * @param warehouseNo 倉庫番号
     * @param companyNo   会社番号
     * @param stockNum    在庫数(バラ)
     * @param reason      在庫履歴用理由
     * @param moveTime    上書きする時刻
     */
    public void overwriteStock(Integer goodsNo, Integer warehouseNo, Integer companyNo, BigDecimal stockNum, StockLogReason reason, LocalDateTime moveTime) throws Exception {
        // 棚卸の在庫数を在庫履歴と在庫（上書き）に登録する
        this.move(companyNo, goodsNo, warehouseNo, moveTime, reason, stockNum, true);
    }

    /**
     * TStockからバラの数を取得します。
     *
     * @param stock 在庫
     * @return バラの数
     */
    public BigDecimal calculateMinGoodsUnitNum(IStockEntity stock) {
        // 商品単位の取得
        List<Integer> unitNoList = new ArrayList<>();
        unitNoList.add(stock.getUnit1No());
        if (stock.getUnit2No() != null) {
            unitNoList.add(stock.getUnit2No());
        }
        if (stock.getUnit3No() != null) {
            unitNoList.add(stock.getUnit3No());
        }

        BigDecimal unit2ContainNum = BigDecimal.ZERO;
        BigDecimal unit3ContainNum = BigDecimal.ZERO;

        if (unitNoList.stream().allMatch(unitNo -> this.unitNoMap.containsKey(unitNo))) {
            for (Integer unitNo : unitNoList) {
                if (stock.getUnit2No() != null && unitNo.equals(stock.getUnit2No())) {
                    unit2ContainNum = this.unitNoMap.get(unitNo);
                }
                if (stock.getUnit3No() != null && unitNo.equals(stock.getUnit3No())) {
                    unit3ContainNum = this.unitNoMap.get(unitNo);
                }
            }
        } else {
            List<MGoodsUnit> unitList = this.goodsUnitService.findByUnitNoList(unitNoList);
            this.goodsUnitMap.put(stock.getGoodsNo(), unitList);
            this.unitNoMap.putAll(unitList.stream().collect(Collectors.toMap(MGoodsUnit::getUnitNo, MGoodsUnit::getContainNum)));
            for (MGoodsUnit unit : unitList) {
                if (stock.getUnit2No() != null && unit.getUnitNo().equals(stock.getUnit2No())) {
                    unit2ContainNum = unit.getContainNum();
                }
                if (stock.getUnit3No() != null && unit.getUnitNo().equals(stock.getUnit3No())) {
                    unit3ContainNum = unit.getContainNum();
                }
            }
        }

        BigDecimal stockNum = stock.getUnit1StockNum();
        if (stock.getUnit2StockNum() != null) {
            stockNum = stockNum.add(unit2ContainNum.multiply(stock.getUnit2StockNum()));
        }
        if (stock.getUnit3StockNum() != null) {
            stockNum = stockNum.add(unit3ContainNum.multiply(stock.getUnit3StockNum()));
        }
        return stockNum;
    }

    /**
     * 在庫履歴の移動時刻からそれ以降の在庫履歴の在庫処理を実行します。
     *
     * @param rerunDateTime 再処理する時刻
     * @throws Exception 例外発生時
     */
    public void rerunStockLog(LocalDateTime rerunDateTime) throws Exception {
        // 再実行時刻(棚卸し)以降の在庫履歴の検索
        List<TStockLog> stockLogList = this.tStockLogService.findByMoveTime(rerunDateTime, null, Flag.NO);
        int size = stockLogList.size();
        int cnt = 0;
        for (TStockLog stockLog : stockLogList) {
            // 在庫処理
            cnt++;
            this.tStockProcess(stockLog, false);
            log.info(String.format("在庫履歴再処理中・・・%d/%d", cnt, size));
        }
    }

    /**
     * 在庫履歴を削除します
     *
     * @param inventoryTime 棚卸時刻
     */
    public void deleteTStockLogForInventory(LocalDateTime inventoryTime) {
        this.tStockLogService.deleteForInventory(inventoryTime);
    }

    /**
     * 特定の在庫移動日時以降の在庫リストを返します
     *
     * @param companyNo    会社番号
     * @param warehouseNo  倉庫番号
     * @param moveTimeFrom 在庫移動日時
     * @return 特定の在庫移動日時以降の在庫リスト
     */
    public List<TStock> findStockList(Integer companyNo, Integer warehouseNo, LocalDateTime moveTimeFrom) {
        List<TStockLog> stockLogList = this.tStockLogService.findByMoveTime(moveTimeFrom, null, Flag.NO);
        List<Integer> goodsNoList = stockLogList.stream().map(TStockLog::getGoodsNo).distinct().collect(Collectors.toList());
        return this.tStockService.find(goodsNoList, companyNo, warehouseNo, Flag.NO);
    }
}
