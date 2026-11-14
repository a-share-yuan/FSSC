package com.fssc.system.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 文件存储（/api/tenant/system/file/**）。
 * 上传 → 返回文件记录；下载两步走：先换一次性令牌，再凭令牌下载（防 URL 猜测越权）。
 */
@RestController
@RequestMapping("/api/tenant/system/file")
@RequiredArgsConstructor
public class TenantFileController {

    private final FileService fileService;
    private final JdbcTemplate jdbc;

    // 上开放开给所有登录租户用户：报销单发票附件为合规必填，任何能提交单据的员工都必须可上传（列表/删除仍需权限）
    @OperLog(module = "SYSTEM_FILE", operType = "INSERT", desc = "上传文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) String bizType,
                                  @RequestParam(required = false) Long bizId,
                                  @RequestParam(required = false) String zone) {
        return Result.success(fileService.upload(file, bizType, bizId, zone));
    }

    // 单文件详情＝预览器取元信息用，边界由下面的 assertFileReadable（上传人／单据申请人／在途受理人／共享作业岗／管理员）判，
    // 不要再叠一道 system:file:list：那是"跨单据全库文件列表"的管理面权限点，sys_menu 100 至今未授任何角色，
    // 结果是谁都点不动【预览】——连看自己刚上传的那张发票都不行。跨单列表面（/page、/storageStats）的校验保持不变。
    @GetMapping("/{id}")
    public Result<SysFile> detail(@PathVariable Long id) {
        assertFileReadable(id);
        return Result.success(fileService.getById(id));
    }

    @RequiresPerm("system:file:list")
    @GetMapping("/page")
    public Result<PageResult<SysFile>> page(PageQuery query,
                                                                 @RequestParam(required = false) String bizType,
                                                                 @RequestParam(required = false) Long bizId) {
        return Result.success(fileService.page(query.getPageNum(), query.getPageSize(), bizType, bizId));
    }

    /**
 * 删除出口二选一：
     *  ① 持有 system:file:delete ＝跨人清理附件的作业面权限（现授档案/财务经理岗），照旧可删；
     *  ② 本人上传、且尚未被任何单据/冷表/电子档案引用的附件＝"传错了自己撤"的自助出口。
     * 原先只有 ①，而该码全库仅 2 家角色持有 ⇒ 员工误传附件后没有正规退路（只能留着，或绕过界面删库）。
     * 引用面按 information_schema 现读枚举：exp_bill_attach / exp_bill_attach_cold / op_archive，不要留下悬空引用。
     */
    @OperLog(module = "SYSTEM_FILE", operType = "DELETE", desc = "删除文件")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        assertFileDeletable(id);
        fileService.delete(id);
        return Result.success();
    }

    private void assertFileDeletable(Long fileId) {
        LoginUser u = UserContext.getRequired();
        Set<String> perms = u.getPerms() == null ? Set.of() : u.getPerms();
        if (perms.contains("*:*:*") || perms.contains("system:file:delete")) return;
        SysFile f = fileService.getById(fileId);
        Long uid = u.getUserId();
        if (f == null || uid == null || !uid.equals(f.getUploadBy())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权删除该附件：仅上传人本人，或具备「附件删除」权限的岗位可删");
        }
        Integer refs = jdbc.queryForObject("/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT ("
                + "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM exp_bill_attach WHERE file_id=? AND del_flag=0) + ("
                + "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM exp_bill_attach_cold WHERE file_id=? AND del_flag=0) + ("
                + "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM op_archive WHERE file_id=? AND del_flag=0)", Integer.class, fileId, fileId, fileId);
        if (refs != null && refs > 0) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该附件已被单据或档案引用，请回到单据上删除附件行；已归档的附件须由具备「附件删除」权限的岗位处理");
        }
    }

    /** 存储占用统计（MB） */
    @RequiresPerm("system:file:list")
    @GetMapping("/storageStats")
    public Result<Map<String, Object>> storageStats() {
        long mb = fileService.calculateStorageMb();
        return Result.success(Map.of("storageMb", mb));
    }

    /**
     * 第一步：申领一次性下载令牌（5 分钟有效）。
     * 与 detail 同一规则：边界由 assertFileReadable 判，不要再叠 system:file:download——
     * 该码只授给少数角色，许多持角色在岗用户（含审批人、财务岗）都没有，
     * 叠上去＝申请人和审批人点自己的影像也 403。
     */
    @PostMapping("/{id}/token")
    public Result<String> createToken(@PathVariable Long id) {
        assertFileReadable(id);
        String token = fileService.createDownloadToken(id);
        // 签发链一旦给出空令牌，绝不能把 data:null 当成功返回——前端会拼出
        // download?token=null 得到 400「令牌无效」，表现为影像永久"加载失败"且用户无可重试提示；
        // 此处显式失败（500 + 可重试文案），把隐性数据缺陷变为可见、可重试的错误。
        if (token == null || token.isBlank() || "null".equals(token)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "下载令牌签发失败，请稍后重试");
        }
        return Result.success(token);
    }

    /** 附件归属鉴权（合规整改）：上传人本人 / 租户管理员 / 单据申请人 / 财务共享作业岗 之外一律拒绝 */
    private void assertFileReadable(Long fileId) {
        Long uid;
        try {
            uid = UserContext.getRequired().getUserId();
        } catch (Exception e) {
            return; // 无登录上下文时交给权限层处理
        }
        if (uid == null) return;
        SysFile f = fileService.getById(fileId);
        if (f == null) return;
        if (uid.equals(f.getUploadBy())) return;
        JdbcTemplate jdbc = this.jdbc;
        Integer admin = jdbc.queryForObject("/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COALESCE(MAX(is_admin),0) FROM sys_user WHERE id=?", Integer.class, uid);
        if (admin != null && admin == 1) return;
        Integer applicant = jdbc.queryForObject(
                "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM exp_bill_attach a JOIN exp_bill b ON b.id=a.bill_id WHERE a.file_id=? AND b.apply_user_id=?",
                Integer.class, fileId, uid);
        if (applicant != null && applicant > 0) return;
        // 当前在途环节的受理人：审批人必须能看到附件与 OCR 结果，
        // 否则"修越权"会顺手把正常审批路径也挡掉（与 的交叉点）。
        Integer assignee = jdbc.queryForObject(
                "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM exp_bill_attach a JOIN wf_task t "
                        + "ON t.biz_id = a.bill_id AND t.biz_type LIKE 'EXP%' AND t.status='PENDING' "
                        + "WHERE a.file_id=? AND t.assignee_id=?",
                Integer.class, fileId, uid);
        if (assignee != null && assignee > 0) return;
        Integer finance = jdbc.queryForObject(
                "/* tenant-ok: 按 file_id/user_id/role_id 全局唯一键定位 */ SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.role_code IN "
                        + "('FIN_AUDIT','SCAN_STAFF','SHARING_SUPPORT','FSSC_AUDITOR','FSSC_SUPERVISOR','FIN_GL','BASE_FIN_MANAGER','PLATE_FIN_MANAGER','COST_STAFF','BIZ_FIN_MANAGER')",
                Integer.class, uid);
        if (finance != null && finance > 0) return;
        throw new BizException(ErrorCode.FORBIDDEN,
                "无权访问该附件：仅上传人、单据申请人、财务共享作业岗或管理员可查看");
    }

    /** 第二步：凭令牌下载（浏览器直链，无 Authorization 头也可用，token 即身份） */
    @GetMapping("/download")
    public void download(@RequestParam String token, HttpServletResponse response) throws IOException {
        Path path = fileService.downloadByToken(token);
        byte[] data = fileService.readBytes(path);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String fileName = URLEncoder.encode(path.getFileName().toString(), StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.setContentLength(data.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
        }
    }
}
