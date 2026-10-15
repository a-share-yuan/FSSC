package com.fssc.document.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.service.ArchiveDestroyService;
import com.fssc.document.service.ArchiveService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenant/compliance/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;
    private final ArchiveDestroyService archiveDestroyService;

    /**
     * 缺陷修复（H-04 / DA-04）：billId 为自由文本单号（如 BX202609120001）时
     * 原 Long.valueOf 直接抛 NumberFormatException → 500「系统异常」。
     * 现统一转友好业务异常（400 + 明确提示），不再把堆栈抛给前端。
     */
    private static Long parseBillId(Object raw) {
        if (raw == null) {
            return 0L;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ex) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID,
                    "归档失败：单据编号须为数字ID，收到非数字编号「" + s + "」。请通过单据列表选择单据后归档");
        }
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "INSERT", desc = "创建档案")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody Map<String, Object> req) {
        Long billId = parseBillId(req.get("billId"));
        // 必填项不再由控制器代填默认值（原 archiveType 兜 "EXPENSE"、archiveYear 兜当年）：
        // 用户没选就写出「报销凭证 / 本年」的档案目录属于造数据，交 ArchiveService 按前端同口径拦下。
        String archiveType = req.get("archiveType") != null ? req.get("archiveType").toString() : null;
        Integer archiveYear = req.get("archiveYear") != null && !req.get("archiveYear").toString().isBlank()
                ? Integer.valueOf(req.get("archiveYear").toString()) : null;
        String archivePeriod = req.get("archivePeriod") != null ? req.get("archivePeriod").toString() : null;
        Integer retentionPeriod = req.get("retentionPeriod") != null && !req.get("retentionPeriod").toString().isBlank()
                ? Integer.valueOf(req.get("retentionPeriod").toString()) : null;
        return Result.success(archiveService.createArchive(billId, archiveType, archiveYear, archivePeriod, retentionPeriod));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:archive")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "执行归档")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        archiveService.archive(id);
        return Result.success();
    }

    /**
     * R5-D-197（内控红线，销毁不可逆）：本端点改前直调 {@code ArchiveService#destroy} 一键置 DISPOSED——
     * 无到期校验、不查销毁审批、不起 FM_DESTROY 流程（实测 id=25 destroy_date=2030-01-01 未届满却已 DISPOSED）。
     * 现整体改道：一律转 {@link ArchiveDestroyService#createDestroyRequest} 发起「销毁申请」，由合规链定论
     * （assertAllExpired 到期校验＋双人复核双签＋FM_DESTROY 起流程）；DISPOSED 只会在审批闭环后的
     * {@link ArchiveDestroyService#executeDestroy} 落地。理由（reason）缺失时由服务层按「请填写销毁理由」
     * 拒绝——旧的无理由一键销毁从此 fail-closed。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:destroy")
    @OperLog(module = "ARCHIVE", operType = "INSERT", desc = "发起档案销毁申请")
    @PostMapping("/{id}/destroy")
    public Result<Long> destroy(@PathVariable Long id,
                                @RequestParam(required = false) String reason) {
        // 申请人取登录态（引擎线程无登录态时保持 null，不造假人）
        com.fssc.framework.security.LoginUser cu = com.fssc.framework.security.UserContext.get();
        Long applicantId = cu == null ? null : cu.getUserId();
        return Result.success(archiveDestroyService.createDestroyRequest(List.of(id), reason, applicantId));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/{id}")
    public Result<OpArchive> getById(@PathVariable Long id) {
        return Result.success(archiveService.getById(id));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/page")
    public Result<PageResult<OpArchive>> page(PageQuery query,
                                                    @RequestParam(required = false) String archiveType,
                                                    @RequestParam(required = false) Integer archiveYear,
                                                    @RequestParam(required = false) String archiveStatus,
                                                    @RequestParam(required = false) String keyword) {
        // D-DEC-256（核实册:325）：新增可选 keyword 入参（档案编号/编码/关联单号/标题，服务端 LIKE）。
        // 不传＝行为与改前完全一致；档案选择器的检索框从此问的是全量目录而不是"已经取回的第一页"，
        // 输入真实档案号不再返 0 条。检索臂口径由 ArchiveService.applyKeywordArms 单点将守。
        return Result.success(archiveService.page(query.getPageNum(), query.getPageSize(),
                archiveType, archiveYear, archiveStatus, keyword));
    }

    /**
     * R5-D-199（读端点写库）：本 GET 为<b>只读语义</b>——服务层只重算比对并返回布尔，不落库
     * （改前服务层在此路径 {@code updateById} 写 integrity_status/update_time，GET 因而
     * 幂等性与只读语义双破、审计时间列被反复点击污染）。状态落库与校验人留痕走显式
     * POST {@code /{id}/verify}（见 {@link #verifyFull}）。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/{id}/verify")
    public Result<Boolean> verify(@PathVariable Long id) {
        return Result.success(archiveService.verifyIntegrity(id));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/expiring")
    public Result<List<OpArchive>> listExpiring(@RequestParam(defaultValue = "30") int days) {
        return Result.success(archiveService.listExpiringSoon(days));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:delete")
    @OperLog(module = "ARCHIVE", operType = "DELETE", desc = "删除档案")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        archiveService.delete(id);
        return Result.success();
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/list")
    public Result<List<OpArchive>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String archiveType,
                                             @RequestParam(required = false) Integer archiveYear) {
        return Result.success(archiveService.search(keyword, archiveType, archiveYear));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Integer year) {
        return Result.success(archiveService.getArchiveStats(year));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "INSERT", desc = "自动归档")
    @PostMapping("/auto-archive")
    public Result<Long> autoArchive(@RequestBody Map<String, Object> req) {
        Long billId = parseBillId(req.get("billId"));
        String archiveType = (String) req.getOrDefault("archiveType", "EXPENSE");
        Integer archiveYear = req.get("archiveYear") != null ? Integer.valueOf(req.get("archiveYear").toString()) : LocalDate.now().getYear();
        String archivePeriod = (String) req.getOrDefault("archivePeriod", "");
        return Result.success(archiveService.autoArchive(billId, archiveType, archiveYear, archivePeriod));
    }

    /**
     * R5-D-199：显式写端点——「完整性校验并落状态」。GET {@code /{id}/verify} 改为只算不写后，
     * 状态持久化（integrity_status）与校验人留痕一律经本 POST 入口；@OperLog 由切面
     * 记录操作人（校验人）入 sys_oper_log/pf_oper_log。
     * R5-D-208：回执改为三态结构（status/verified/message：VERIFIED/MISMATCH/UNVERIFIABLE），
     * 前端按三句文案成像，无法校验不再与篡改同形。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "档案完整性校验")
    @PostMapping("/{id}/verify")
    public Result<Map<String, Object>> verifyFull(@PathVariable Long id) {
        return Result.success(archiveService.verifyIntegrityFull(id));
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/search")
    public Result<List<OpArchive>> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String archiveType,
                                                @RequestParam(required = false) Integer archiveYear) {
        return Result.success(archiveService.search(keyword, archiveType, archiveYear));
    }
}
