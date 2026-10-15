package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.common.core.ErrorCode;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpBarcode;
import com.fssc.sharing.entity.OpPhysicalDelivery;
import com.fssc.sharing.service.BarcodeService;
import com.fssc.sharing.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P0-5 实物流签收（TC12：电子流 + 实物流双到达）。
 * 增强功能：条码扫描签收、批量签收、拒签退件、快递信息录入、交单登记。
 * 对标 SAP MM 收货、用友 U8 到货签收、金蝶 K3 Cloud 采收。
 */
@RestController
@RequestMapping("/api/tenant/sharing/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final ExpBillMapper expBillMapper;
    private final BarcodeService barcodeService;

    /** 待签收清单（兼容旧接口） */
    @GetMapping("/pending")
    @RequiresPerm("sharing:delivery:pending")
    public Result<PageResult<OpPhysicalDelivery>> pending(PageQuery query) {
        return Result.success(deliveryService.pending(query.getPageNum(), query.getPageSize()));
    }

    /**
     * 签收单分页查询（支持多条件筛选：状态/单号/运单号/交付方式/关键词）。
     * 对标 SAP MD04 收货清单筛选。
     */
    @GetMapping("/page")
    @RequiresPerm("sharing:delivery:pending")
    public Result<PageResult<OpPhysicalDelivery>> page(PageQuery query,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String billNo,
                                                        @RequestParam(required = false) String expressNo,
                                                        @RequestParam(required = false) String deliveryType,
                                                        @RequestParam(required = false) String keyword) {
        return Result.success(deliveryService.page(query.getPageNum(), query.getPageSize(),
                status, billNo, expressNo, deliveryType, keyword));
    }

    @GetMapping("/byBill/{billId}")
    public Result<List<OpPhysicalDelivery>> byBill(@PathVariable Long billId) {
        return Result.success(deliveryService.byBill(billId));
    }

    /**
     * 单条签收（R5-D-241：支持签收备注）。
     * body 可省；Body: { "signRemark": "已当面清点" }
     */
    @PostMapping("/sign/{id}")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "实物流签收")
    public Result<Void> sign(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        var u = UserContext.getRequired();
        String signRemark = body == null ? null : (String) body.get("signRemark");
        deliveryService.sign(id, u.getUserId(), u.getRealName(), signRemark);
        return Result.success();
    }

    /**
     * 批量签收（条码枪逐个扫描后批量提交）。
     * Body: { "ids": [1,2,3], "signRemark": "备注" }
     */
    @PostMapping("/batch-sign")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "批量签收")
    public Result<Integer> batchSign(@RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream().map(Number::longValue).toList();
        String signRemark = (String) body.get("signRemark");
        int count = deliveryService.batchSign(ids, u.getUserId(), u.getRealName(), signRemark);
        return Result.success(count);
    }

    /**
     * 扫码签收（条码枪扫描值：条码/单据号/运单号）。
     * Body: { "scanValue": "BL20250904120001", "signRemark": "备注" }
     */
    @PostMapping("/scan-sign")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "扫码签收")
    public Result<OpPhysicalDelivery> scanSign(@RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        String scanValue = (String) body.get("scanValue");
        if (scanValue == null || scanValue.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "扫描值不能为空");
        }
        String signRemark = (String) body.get("signRemark");
        OpPhysicalDelivery result = deliveryService.scanSign(scanValue, u.getUserId(), u.getRealName(), signRemark);
        return Result.success(result);
    }

    /**
     * 拒签退件。
     * Body: { "rejectReason": "单据不完整" }
     */
    @PostMapping("/{id}/reject")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "拒签退件")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        String rejectReason = (String) body.get("rejectReason");
        deliveryService.reject(id, u.getUserId(), u.getRealName(), rejectReason);
        return Result.success();
    }

    /**
     * 员工交单登记（含快递信息录入）。
     * Body: { "billId": 123, "expressCompany": "顺丰", "expressNo": "SF123456", "packageCount": 1, "deliveryType": "POST", "remark": "..." }
     */
    @PostMapping("/apply")
    @OperLog(module = "SHARING", operType = "INSERT", desc = "交单登记")
    public Result<Long> apply(@RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        Long billId = ((Number) body.get("billId")).longValue();
        String expressCompany = (String) body.get("expressCompany");
        String expressNo = (String) body.get("expressNo");
        Integer packageCount = body.get("packageCount") != null ? ((Number) body.get("packageCount")).intValue() : 1;
        String deliveryType = (String) body.get("deliveryType");
        String remark = (String) body.get("remark");
        Long id = deliveryService.applyDelivery(billId, expressCompany, expressNo,
                packageCount, deliveryType, remark, u.getUserId(), u.getRealName());
        return Result.success(id);
    }

    /** 员工查看自己的交单记录 */
    @GetMapping("/my-deliveries")
    public Result<PageResult<OpPhysicalDelivery>> myDeliveries(PageQuery query) {
        var u = UserContext.getRequired();
        return Result.success(deliveryService.myDeliveries(query.getPageNum(), query.getPageSize(), u.getUserId()));
    }

    /** 对指定已通过单据登记实物流待签收（无纸化豁免则自动置 NOT_REQUIRED） */
    @PostMapping("/register/{billId}")
    @OperLog(module = "SHARING", operType = "INSERT", desc = "登记实物流待签收")
    public Result<Void> register(@PathVariable Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        deliveryService.registerIfNeeded(bill);
        return Result.success();
    }

    // ==================== 条码管理 ====================

    /**
     * 条码分页查询。
     */
    @GetMapping("/barcode/page")
    @RequiresPerm("sharing:delivery:pending")
    public Result<PageResult<OpBarcode>> barcodePage(PageQuery query,
                                                      @RequestParam(required = false) String bizType,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(barcodeService.page(query.getPageNum(), query.getPageSize(), bizType, status, keyword));
    }

    /**
     * 按条码值查询（条码枪扫描后反查业务信息）。
     */
    @GetMapping("/barcode/{barcodeValue}")
    public Result<OpBarcode> findByBarcode(@PathVariable String barcodeValue) {
        return Result.success(barcodeService.findByBarcode(barcodeValue));
    }

    /**
     * 批量生成条码。
     * Body: { "bizType": "BILL", "refIds": [1,2], "refNos": ["BL001","BL002"] }
     */
    @PostMapping("/barcode/generate")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "INSERT", desc = "生成条码")
    public Result<List<Long>> generateBarcodes(@RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        String bizType = (String) body.get("bizType");
        @SuppressWarnings("unchecked")
        List<Long> refIds = ((List<Number>) body.get("refIds")).stream().map(Number::longValue).toList();
        @SuppressWarnings("unchecked")
        List<String> refNos = (List<String>) body.get("refNos");
        return Result.success(barcodeService.generateBarcodes(bizType, refIds, refNos, u.getUserId(), u.getRealName()));
    }

    /**
     * 档案上架（扫码指定位置）。
     * Body: { "barcode": "BX20250904120001", "location": "A-01-03" }
     */
    @PostMapping("/barcode/shelve")
    @RequiresPerm("sharing:delivery:sign")
    @OperLog(module = "SHARING", operType = "UPDATE", desc = "档案上架")
    public Result<Void> shelve(@RequestBody Map<String, Object> body) {
        var u = UserContext.getRequired();
        String barcode = (String) body.get("barcode");
        String location = (String) body.get("location");
        barcodeService.shelve(barcode, location, u.getUserId(), u.getRealName());
        return Result.success();
    }
}