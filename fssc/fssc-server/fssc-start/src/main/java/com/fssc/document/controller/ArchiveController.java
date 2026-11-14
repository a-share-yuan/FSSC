package com.fssc.document.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.entity.OpArchiveItem;
import com.fssc.document.service.ArchiveDestroyService;
import com.fssc.document.service.ArchiveService;
import com.fssc.document.service.ArchiveVolumeService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
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
@FsscDataSource("document")
public class ArchiveController {

    private final ArchiveService archiveService;
    private final ArchiveDestroyService archiveDestroyService;
    /** 组卷编排（一卷多件） */
    private final ArchiveVolumeService archiveVolumeService;
    /** 卷内文件取流（档案域这条腿，不要绕开共享附件白名单） */
    private final FileService fileService;

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
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
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
        // 用户没选就写出「报销凭证 / 本年」的档案目录属于造数据，交 ArchiveService 按前端同一规则拦下。
        String archiveType = req.get("archiveType") != null ? req.get("archiveType").toString() : null;
        Integer archiveYear = req.get("archiveYear") != null && !req.get("archiveYear").toString().isBlank()
                ? Integer.valueOf(req.get("archiveYear").toString()) : null;
        String archivePeriod = req.get("archivePeriod") != null ? req.get("archivePeriod").toString() : null;
        Integer retentionPeriod = req.get("retentionPeriod") != null && !req.get("retentionPeriod").toString().isBlank()
                ? Integer.valueOf(req.get("retentionPeriod").toString()) : null;
        // ARC-M4：载体类型由建卷弹窗透传，缺省由服务层归一为 PAPER（严格侧）
        String carrierType = req.get("carrierType") != null ? req.get("carrierType").toString() : null;
        return Result.success(archiveService.createArchive(billId, archiveType, archiveYear, archivePeriod,
                retentionPeriod, carrierType));
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
     * 内控红线，销毁不可逆：本端点原先直调 {@code ArchiveService#destroy} 一键置 DISPOSED——
     * 无到期校验、不查销毁审批、不起 FM_DESTROY 流程（未届满的档案也能被直接置 DISPOSED）。
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
        LoginUser cu = UserContext.get();
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
        // 核实册:325：新增可选 keyword 入参（档案编号/编码/关联单号/标题，服务端 LIKE）。
        // 不传＝行为与原先完全一致；档案选择器的检索框从此问的是全量目录而不是"已经取回的第一页"，
        // 输入真实档案号不再返 0 条。检索臂规则由 ArchiveService.applyKeywordArms 单点将守。
        return Result.success(archiveService.page(query.getPageNum(), query.getPageSize(),
                archiveType, archiveYear, archiveStatus, keyword));
    }

    /**
     * 读端点写库：本 GET 为<b>只读语义</b>——服务层只重算比对并返回布尔，不写入数据库
     * （原先服务层在此路径 {@code updateById} 写 integrity_status/update_time，GET 因而
     * 去重特性与只读语义双破、审计时间列被反复点击污染）。状态写入数据库与校验人记录走显式
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
     * 显式写端点——「完整性校验并落状态」。GET {@code /{id}/verify} 改为只算不写后，
     * 状态持久化（integrity_status）与校验人记录一律经本 POST 入口；@OperLog 由切面
     * 记录操作人（校验人）入 sys_oper_log/pf_oper_log。
     * 响应改为三态结构（status/verified/message：VERIFIED/MISMATCH/UNVERIFIABLE），
     * 前端按三句文案成像，无法校验不再与篡改同形。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "档案完整性校验")
    @PostMapping("/{id}/verify")
    public Result<Map<String, Object>> verifyFull(@PathVariable Long id) {
        return Result.success(archiveService.verifyIntegrityFull(id));
    }

    /**
     * ARC-M3维护端点：<b>按卷重算并回灌</b>完整性哈希。
     *
     * <p>唯一合法用途＝档案编号/编码结构改造（如加全宗前缀）后重新封存快照——哈希算式把
     * {@code no=}/{@code code=} 与载体文件 MD5 都串进输入，改号必然使全部在册卷转 MISMATCH，
     * 而 SQL 算不出文件 MD5 串。它不是"把状态刷绿"的旁路：重算用的是真实快照，
     * 文件若真被改动，后续 {@code /verify} 仍会报不一致。
     *
     * <p>写码沿用 {@code compliance:archive:archive}（执行归档同一枚）：改的是卷的归档结论，
     * 不是普通目录编辑；不为此新造权限码。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:archive")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "按卷重算完整性哈希")
    @PostMapping("/{id}/resign")
    public Result<Map<String, Object>> resign(@PathVariable Long id) {
        return Result.success(archiveService.resignIntegrity(id));
    }

    /**
     * ARC-M4：改卷的载体类型（纸质/电子）。只有待归档（PENDING）的卷可改——
     * 审批在途/已定稿改载体＝审批人看的与归档结论不是同一份。写码同卷内件维护（create）。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "修改案卷载体类型")
    @PostMapping("/{id}/carrier")
    public Result<Void> updateCarrier(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        archiveService.updateCarrier(id, req.get("carrierType") == null ? null : req.get("carrierType").toString());
        return Result.success();
    }

    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/search")
    public Result<List<OpArchive>> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String archiveType,
                                                @RequestParam(required = false) Integer archiveYear) {
        return Result.success(archiveService.search(keyword, archiveType, archiveYear));
    }

    // 组卷改造：案卷 ↔ 卷内件（一卷多件）
    // 权限沿用既有三个码（list 读、create 写），不要新造权限码——档案岗/档案主管已持有，
    // 加新码＝又要过一轮全站授权核查窗。

    /** 卷内目录（件号正序，含文件数）。 */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/{id}/items")
    public Result<List<OpArchiveItem>> items(@PathVariable Long id) {
        return Result.success(archiveService.listVolumeItems(id));
    }

    /** 新建案卷并一次挂多件（顺序即件号）。 */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "INSERT", desc = "新建案卷（多件入卷）")
    @PostMapping("/create-volume")
    public Result<Long> createVolume(@RequestBody Map<String, Object> req) {
        String archiveType = req.get("archiveType") == null ? null : req.get("archiveType").toString();
        Integer archiveYear = req.get("archiveYear") == null ? null : Integer.valueOf(req.get("archiveYear").toString());
        String archivePeriod = req.get("archivePeriod") == null ? null : req.get("archivePeriod").toString();
        Integer retention = req.get("retentionPeriod") == null ? null : Integer.valueOf(req.get("retentionPeriod").toString());
        // ARC-M4：组卷入口与单卷建卷共用同一载体口径；缺省归一为 PAPER
        String carrierType = req.get("carrierType") == null ? null : req.get("carrierType").toString();
        return Result.success(archiveVolumeService.createVolume(archiveType, archiveYear, archivePeriod, retention,
                parseBillIds(req.get("billIds")), carrierType));
    }

    /** 向待归档卷追加件（同卷重复加件＝幂等，全重复时如实报错）。 */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "案卷追加卷内件")
    @PostMapping("/{id}/items")
    public Result<Integer> addItems(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        return Result.success(archiveVolumeService.addItems(id, parseBillIds(req.get("billIds"))));
    }

    /**
     * 件级编页（ARC-M4）：起页＋页数一起给，两者同为 0/空＝清除该件页号。
     *
     * <p>写码与"增删卷内件"同一枚（{@code compliance:archive:create}）：编页改的是卷内目录著录项，
     * 不动单据、不动卷；可改窗口也同一把尺（审批在途／已定稿拒绝）。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "UPDATE", desc = "案卷件级编页")
    @PostMapping("/{id}/items/{itemId}/pages")
    public Result<Void> updateItemPages(@PathVariable Long id, @PathVariable Long itemId,
                                        @RequestBody Map<String, Object> req) {
        archiveService.updateItemPages(id, itemId,
                req.get("pageFrom") == null ? null : Integer.valueOf(req.get("pageFrom").toString()),
                req.get("pageCount") == null ? null : Integer.valueOf(req.get("pageCount").toString()));
        return Result.success();
    }

    /** 把一件移出本卷（只解绑，不要动单据、不要动卷）。 */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "DELETE", desc = "案卷移出卷内件")
    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        archiveVolumeService.removeItem(id, itemId);
        return Result.success();
    }

    /**
     * 删除空案卷（仅「待归档」且卷内 0 件）。
     *
     * <p>建错卷、或件全部移出后留下的空壳，此前没有出口：不能执行归档（入口闸拦空卷），
     * 也没有删除入口。写码与建卷同一个（{@code compliance:archive:create}）——删的是登记，不是档案。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:create")
    @OperLog(module = "ARCHIVE", operType = "DELETE", desc = "删除空案卷")
    @DeleteMapping("/{id}")
    public Result<Void> deleteEmptyVolume(@PathVariable Long id) {
        archiveService.deleteEmptyVolume(id);
        return Result.success();
    }

    /**
     * 档案岗看自己案卷里有哪些文件、叫什么名。
     *
     * <p>原先档案页【查看影像】绕到「单据详情＋共享附件令牌」两条腿，两者都按<b>单据归属</b>白名单限定
     * （报销人／代理人／该单流程办理人／10 枚财务共享作业岗），档案岗不在其中 ⇒ 复核人看不到影像，
     * 归档审批那节只剩签字。本端点的边界＝卷本身可见（{@code ArchiveService.getById} 同一把尺）
     * ＋文件必须来自该卷的卷内件清单。不要把档案岗加进共享附件白名单（＝任意档案岗可读全租户任意文件）。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @GetMapping("/{id}/files")
    public Result<List<Map<String, Object>>> volumeFiles(@PathVariable Long id) {
        archiveService.getById(id);
        List<Long> fileIds = new ArrayList<>();
        Map<Long, Integer> seqByFile = new LinkedHashMap<>();
        for (OpArchiveItem item : archiveService.listVolumeItems(id)) {
            if (item.getFileIds() == null || item.getFileIds().isBlank()) {
                continue;
            }
            for (String s : item.getFileIds().split(",")) {
                if (s.isBlank()) {
                    continue;
                }
                Long fid = Long.valueOf(s.trim());
                fileIds.add(fid);
                seqByFile.put(fid, item.getSeqNo());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!fileIds.isEmpty()) {
            for (SysFile f : fileService.listByIds(fileIds)) {
                names.put(f.getId(), f.getFileName());
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long fid : fileIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", fid);
            row.put("fileName", names.get(fid));
            row.put("seqNo", seqByFile.get(fid));
            out.add(row);
        }
        return Result.success(out);
    }

    /**
     * 卷内校验通过后签发一次性下载令牌。
     * 令牌本身仍由 {@code FileService} 发，取流走既有 {@code /system/file/download?token=}
     * （token 即身份，不再叠加单据归属白名单）⇒ 前端影像查看器零改动即可复用。
     */
    @RequiresFeature("FSSC_ARCHIVE")
    @RequiresPerm("compliance:archive:list")
    @OperLog(module = "ARCHIVE", operType = "OTHER", desc = "案卷卷内文件取流")
    @PostMapping("/{id}/files/{fileId}/token")
    public Result<String> volumeFileToken(@PathVariable Long id, @PathVariable Long fileId) {
        archiveService.getById(id);
        boolean owned = false;
        for (OpArchiveItem item : archiveService.listVolumeItems(id)) {
            if (item.getFileIds() == null || item.getFileIds().isBlank()) {
                continue;
            }
            for (String s : item.getFileIds().split(",")) {
                if (s.trim().equals(String.valueOf(fileId))) {
                    owned = true;
                    break;
                }
            }
        }
        if (!owned) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该文件不在本案卷的卷内清单里，无权调取");
        }
        String token = fileService.createDownloadToken(fileId);
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "下载令牌签发失败，请稍后重试");
        }
        return Result.success(token);
    }

    /** billIds 入参解析：接受数组或逗号串；不要静默丢掉非法项（丢一项就少立一件，属数据缺陷）。 */
    private static List<Long> parseBillIds(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        List<?> list = raw instanceof List<?> l ? l
                : Arrays.stream(raw.toString().split(",")).map(s -> (Object) s).collect(Collectors.toList());
        for (Object o : list) {
            if (o == null || o.toString().isBlank()) {
                continue;
            }
            try {
                out.add(Long.valueOf(o.toString().trim()));
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "入卷单据须为数字ID，收到「" + o + "」；请从待归档清单勾选单据");
            }
        }
        return out;
    }
}
