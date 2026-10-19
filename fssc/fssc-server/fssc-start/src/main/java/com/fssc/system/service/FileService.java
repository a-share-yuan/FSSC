// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysFile;
import com.fssc.system.entity.SysFileToken;
import com.fssc.system.mapper.SysFileMapper;
import com.fssc.system.mapper.SysFileTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 文件服务（sys_file + sys_file_token）。
 * <p>
 * 存储路径：{local-root}/{zone}/{tenantId}/{bizType}/{yyyyMM}/{billId}/{uuid}.{ext}（租户目录物理隔离；
 * bizType 空或含非法字符时该段为 COMMON，见 {@link #sanitizeSegment}）。
 * <p>
 * 落盘口径（C-11）：磁盘文件名 = 随机 UUID + 净化后的后缀（只留 [a-z0-9]、至多 8 位），
 * 客户端给的原始名只存 {@code sys_file.file_name} 供展示与下载，不参与拼路径；
 * 落盘前再判一次"目标必须在 local-root 之内"，所以原始名里的 {@code ../}、
 * {@code .jsp.}、{@code .html} 之类线索既拼不出根外路径，也不会留下可按名字处理的文件。
 * <p>
 * 下载两步走：POST /file/{id}/token 换一次性令牌（5分钟）→ GET /file/download?token=xx
 * （令牌全局唯一且用后即焚，杜绝 URL 猜测越权跨租户下载）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final SysFileMapper fileMapper;
    private final SysFileTokenMapper tokenMapper;

    /** 本地存储根目录，可在 yml 配置 fssc.file.local-root 覆盖 */
    @Value("${fssc.file.local-root:./data/files}")
    private String localRoot;

    /** 上传文件大小限制（字节），默认 20MB */
    @Value("${fssc.file.max-size:20971520}")
    private long maxFileSize;

    /** 允许的扩展名白名单（逗号分隔），空则允许所有 */
    @Value("${fssc.file.allowed-extensions:}")
    private String allowedExtensions;

    /** 存储区域子目录（三区逻辑隔离，可在 yml fssc.file.zones.* 覆盖） */
    @Value("${fssc.file.zones.temp:temp}")
    private String zoneTempDir;
    @Value("${fssc.file.zones.business:business}")
    private String zoneBusinessDir;
    @Value("${fssc.file.zones.archive:archive}")
    private String zoneArchiveDir;

    private static final String TOKEN_TTL_MINUTES = "5";

    /** 存储区域解析：TEMP→temp / ARCHIVE→archive / 其他默认 business */
    private String zoneDir(String zone) {
        if (zone == null) return zoneBusinessDir;
        return switch (zone.toUpperCase()) {
            case "TEMP" -> zoneTempDir;
            case "ARCHIVE" -> zoneArchiveDir;
            default -> zoneBusinessDir;
        };
    }

    /** 落盘后缀净化后的最大长度（防"超长后缀名"占满目录项/文件名） */
    private static final int MAX_SUFFIX_LEN = 8;

    /**
     * C-11：配置放不开的下限表——按 BCP 侧 {@code FileUploadPolicy.FORBIDDEN_EXTENSIONS} 的口径取
     * FSSC 用得到的那一段（网页/主动内容、脚本、可执行与载体、Office 宏文档；编译产物 {@code .o/.a}
     * 这类业务上不会出现的项未列）。
     * <p>{@code fssc.file.allowed-extensions} 是白名单：它被外部配置成空值时不校验（fail-open）。
     * 这张表不是配置项，命中即拒，所以"白名单被清空 ⇒ .jsp/.html/.exe 也能收"这一路被封死。</p>
     */
    private static final Set<String> FORBIDDEN_SUFFIXES = Set.of(
            // 网页 / 主动内容（下载端点一旦按类型回吐即可被浏览器渲染）
            "html", "htm", "htmls", "xhtml", "xht", "shtml", "hta", "svg", "svgz", "swf",
            // 脚本
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "jsp", "jspf", "jspx", "php", "php3", "php4",
            "php5", "phtml", "asp", "aspx", "ascx", "asmx", "vbs", "vbe", "jse", "wsf", "wsh",
            "ps1", "psm1", "psd1", "sh", "bash", "zsh", "ksh", "csh", "py", "pyc", "rb", "pl",
            "pm", "lua", "awk", "ahk",
            // 可执行 / 安装包 / 系统映像
            "exe", "dll", "so", "dylib", "ocx", "sys", "drv", "com", "scr", "msi", "msp", "msm",
            "jar", "war", "ear", "class", "java", "dex", "elf", "obj", "lib", "bin", "apk", "xap",
            "aab", "reg", "cpl", "msc", "lnk", "url", "scf", "chm", "hlp", "wim", "iso", "img",
            "vhd", "vhdx", "386",
            // Office 宏载体（无宏的 doc/docx/xls/xlsx/ppt/pptx 在白名单内，不受影响）
            "docm", "dotm", "xlsm", "xltm", "xlam", "pptm", "potm", "ppam");

    /**
     * C-11：从客户端给的原始名里取"最后一个点之后"的一段，并只保留 {@code [a-z0-9]}。
     * <p>目的有三：① {@code a.jsp/../x.png}、{@code a.png/../../evil} 这类含分隔符与 {@code ..}
     * 的名字拼不出根外路径；② 磁盘名不会出现 {@code .jsp.}/{@code .html} 这类可被按名字而非按内容
     * 处理的线索；③ 与原始名彻底解耦——落盘名 = UUID + 本方法返回值。</p>
     *
     * @return 小写、纯字母数字、至多 {@value #MAX_SUFFIX_LEN} 位；无有效后缀返回空串
     */
    private static String sanitizeSuffix(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String tail = originalName.substring(dot + 1).toLowerCase();
        StringBuilder sb = new StringBuilder(Math.min(tail.length(), MAX_SUFFIX_LEN));
        for (int i = 0; i < tail.length() && sb.length() < MAX_SUFFIX_LEN; i++) {
            char c = tail.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * C-11：目录段净化（bizType 由调用方传入，可能带 "/"、".."）。空或全非法时归入 COMMON，
     * 与改造前的默认段名一致，历史文件的读取口径不变。
     */
    private static String sanitizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "COMMON";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 32; i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "COMMON" : sb.toString();
    }

    // ---------- 上传 ----------

    /** 上传文件（默认业务正式影像区 business），返回文件记录。
     *  分库改造：sys_file 在 master 库；REQUIRES_NEW 使被 image 域事务调用时
     *  挂起外层 image 事务、绑定 master 连接独立提交（文件落库不再随影像批次回滚，
     *  跨库不可 ACID 为分库既定代价，孤儿文件由存储对账兜底）。 */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public SysFile upload(MultipartFile multipartFile, String bizType, Long bizId) {
        return upload(multipartFile, bizType, bizId, "BUSINESS");
    }

    /**
     * 上传文件（存储设计：逻辑 Key 分层 {zone}/{tenant}/{bizType}/{yyyyMM}/{billId}/{uuid}.{ext}）。
     * zone：TEMP 临时附件（草稿）/ BUSINESS 业务正式影像（审批中）/ ARCHIVE 归档档案（已归档）。
     * 内部文件名用 UUID 随机串 + 扩展名，不使用原始中文文件名（规避重名覆盖/中文乱码）。
     * 分库改造：sys_file 在 master 库，REQUIRES_NEW 绑定 master 连接独立提交（语义同 3 参重载）。
     */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public SysFile upload(MultipartFile multipartFile, String bizType, Long bizId, String zone) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        // 文件大小校验
        if (multipartFile.getSize() > maxFileSize) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "文件大小超过限制（最大 " + (maxFileSize / 1024 / 1024) + "MB），当前大小 " +
                            (multipartFile.getSize() / 1024 / 1024) + "MB");
        }
        Long tenantId = TenantContext.getTenantId();
        String original = multipartFile.getOriginalFilename() == null ? "unnamed" : multipartFile.getOriginalFilename();
        // C-11：后缀只取最后一个点之后的部分，并且只留 [a-z0-9]（截到 8 位）。
        // 原始名里的 "/"、"\\"、".."、".jsp."、".html." 一类线索一律进不了磁盘名，也进不了 sys_file.file_suffix；
        // 这样即使 fssc.file.allowed-extensions 被外部配置成空（该键空值即不校验，fail-open），
        // 落盘名仍是"UUID + 干净后缀"，不会因为客户端给的文件名而失控。
        String suffix = sanitizeSuffix(original);
        // C-11 下限闸：先于白名单判，且不依赖任何配置项——白名单被外部清空时这一条仍然成立。
        if (FORBIDDEN_SUFFIXES.contains(suffix)) {
            log.warn("upload rejected by forbidden extension floor: original={}, suffix={}", original, suffix);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该格式不能作为附件上传，请改为常见格式（PDF、图片、Office 文档、压缩包等）");
        }
        // 扩展名白名单校验
        if (StringUtils.hasText(allowedExtensions)) {
            String[] exts = allowedExtensions.split(",");
            boolean allowed = false;
            for (String ext : exts) {
                if (ext.trim().equalsIgnoreCase(suffix)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                // 对用户只说"怎么办"：配置原文/内部清单只进日志，不进响应文案
                log.warn("upload rejected by extension whitelist: original={}, suffix={}", original, suffix);
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "不支持的文件格式，请改为常见格式（PDF、图片、Office 文档、压缩包等）后重新上传");
            }
        }
        String storageName = UUID.randomUUID().toString().replace("-", "") + (suffix.isEmpty() ? "" : "." + suffix);
        // 分层逻辑 Key：{zone}/{tenantId}/{bizType}/{yyyyMM}/{billId}/{uuid}.{ext}
        String zoneDir = zoneDir(zone);
        String billSegment = bizId == null ? "NOBILL" : String.valueOf(bizId);
        String relDir = zoneDir + "/" + tenantId + "/"
                + sanitizeSegment(bizType) + "/"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "/" + billSegment;
        String relPath = relDir + "/" + storageName;

        // C-11：目录闸。落盘目标 normalize 后必须仍在 local-root 之内 ——
        // bizType 由调用方传入、zone 由配置给出，任一含 ".." 都能把文件写到根外，
        // 后缀净化只保住文件名，这一段保住目录。越界即拒绝落库与落盘。
        Path root = Paths.get(localRoot).toAbsolutePath().normalize();
        Path target = root.resolve(relPath).normalize();
        if (!target.startsWith(root)) {
            log.warn("upload rejected by storage root guard: relPath={} escapes localRoot", relPath);
            throw new BizException(ErrorCode.PARAM_INVALID, "上传参数不合法，请重新选择文件后上传");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = multipartFile.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            // 具体原因（磁盘/权限/路径）进日志，响应里只给可转述的话，不回显后端原文
            log.error("file save failed: path={}", relPath, e);
            throw new BizException(ErrorCode.PARAM_INVALID, "文件保存失败，请稍后重试或联系管理员");
        }

        SysFile f = new SysFile();
        f.setTenantId(tenantId);
        f.setFileName(original);
        f.setFilePath(relPath);
        f.setFileSuffix(suffix);
        f.setFileSize(multipartFile.getSize());
        f.setContentType(multipartFile.getContentType());
        f.setFileMd5(md5(multipartFile));
        f.setStorageType("LOCAL");
        f.setStorageZone(zone == null ? "BUSINESS" : zone.toUpperCase());
        f.setBizType(bizType == null || bizType.isBlank() ? "COMMON" : bizType);
        f.setBizId(bizId);
        f.setUploadBy(userId());
        fileMapper.insert(f);
        log.info("file uploaded: id={}, tenantId={}, path={}", f.getId(), tenantId, relPath);
        return f;
    }

    /** 计算当前租户的文件存储总占用（字节），结果转换为 MB */
    public long calculateStorageMb() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return 0;
        Long totalBytes = fileMapper.selectSumFileSizeByTenant(tenantId);
        return totalBytes == null ? 0 : totalBytes / (1024 * 1024);
    }

    /** 按 ID 查文件记录（租户隔离由拦截器保证）。
     *  分库改造：sys_file 在 master 库；NOT_SUPPORTED 挂起外层（可能为 image 库）事务后取 master 连接。 */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public SysFile getById(Long id) {
        SysFile f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不存在: " + id);
        }
        return f;
    }

    /** 文件列表分页（管理端） */
    public PageResult<SysFile> page(long pageNum, long pageSize, String bizType, Long bizId) {
        LambdaQueryWrapper<SysFile> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(SysFile::getBizType, bizType);
        }
        if (bizId != null) {
            qw.eq(SysFile::getBizId, bizId);
        }
        qw.orderByDesc(SysFile::getId);
        Page<SysFile> p = fileMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // ---------- 一次性令牌下载 ----------

    /**
     * 删除文件（逻辑删；同时作废未使用的令牌）
     *
     * <p>两表成对写（sys_file_token + sys_file），缺一侧会留下"文件已删但令牌仍有效"的半截状态
     * （ROOT-11 已裁口径：跨表写成对者补边界；自定，无厂商条款）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        tokenMapper.delete(new LambdaQueryWrapper<SysFileToken>()
                .eq(SysFileToken::getFileId, id)
                .eq(SysFileToken::getUsed, 0));
        fileMapper.deleteById(id);
    }

    /** 申领下载令牌（5 分钟有效，一次性） */
    public String createDownloadToken(Long fileId) {
        SysFile f = getById(fileId);
        Long tenantId = TenantContext.getTenantId();
        SysFileToken t = new SysFileToken();
        t.setTenantId(tenantId);
        t.setToken(UUID.randomUUID().toString().replace("-", ""));
        t.setFileId(f.getId());
        t.setUserId(userId());
        t.setExpireTime(LocalDateTime.now().plusMinutes(Long.parseLong(TOKEN_TTL_MINUTES)));
        t.setUsed(0);
        tokenMapper.insert(t);
        return t.getToken();
    }

    /**
     * 凭令牌取文件（并标记已用）。
     * 注意：token 全局唯一，下载校验时可能尚无租户上下文 —— 这里显式按 token 查，
     * 再校验 file 归属同一租户，双重保险。
     */
    @Transactional(rollbackFor = Exception.class)
    public Path downloadByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少下载令牌");
        }
        // 令牌全局唯一，可能跨租户上下文，用 runIgnoreTenant 查
        final SysFileToken[] holder = new SysFileToken[1];
        TenantContext.runIgnoreTenant(() -> holder[0] = tokenMapper.selectOne(
                new LambdaQueryWrapper<SysFileToken>().eq(SysFileToken::getToken, token)));
        SysFileToken t = holder[0];
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "令牌无效");
        }
        if (t.getUsed() != null && t.getUsed() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "令牌已使用");
        }
        if (t.getExpireTime() == null || t.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "令牌已过期");
        }
        final SysFile[] fileHolder = new SysFile[1];
        TenantContext.runIgnoreTenant(() -> fileHolder[0] = fileMapper.selectById(t.getFileId()));
        SysFile f = fileHolder[0];
        if (f == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不存在");
        }
        // 令牌与文件必须同租户
        if (!java.util.Objects.equals(t.getTenantId(), f.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权下载该文件");
        }
        // 用后即焚（token 校验阶段可能无租户上下文，必须在 ignore 模式下执行 UPDATE）
        final SysFileToken mark = new SysFileToken();
        mark.setId(t.getId());
        mark.setUsed(1);
        TenantContext.runIgnoreTenant(() -> tokenMapper.updateById(mark));

        Path p = Paths.get(localRoot).resolve(f.getFilePath()).normalize();
        if (!Files.exists(p)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件已丢失");
        }
        return p;
    }

    /** 读取文件字节（Controller 里配合 Content-Disposition 输出） */
    public byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件读取失败");
        }
    }

    /**
     * 读取已入库文件的字节（供 OCR 本地版式解析按 filePath 拼接存储根读取磁盘）。
     * 记录缺失/文件丢失一律 BizException，由调用方决定降级或标记 FAILED。
     */
    public byte[] readBytes(SysFile f) {
        if (f == null || f.getFilePath() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件记录缺失，无法读取");
        }
        Path p = Paths.get(localRoot).resolve(f.getFilePath()).normalize();
        if (!Files.exists(p)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件已丢失: " + f.getId());
        }
        return readBytes(p);
    }

    // ---------- 归档迁移（存储区域切换） ----------

    /**
     * 归档迁移：文件从当前存储区移动到目标区（如 business → archive），
     * 物理移动 + 更新 file_path/storage_zone。已处于目标区则跳过（幂等）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysFile moveZone(Long fileId, String targetZone) {
        SysFile f = fileMapper.selectById(fileId);
        if (f == null || f.getFilePath() == null) {
            return f;
        }
        String targetDir = zoneDir(targetZone);
        String curDir = f.getFilePath().substring(0, f.getFilePath().indexOf('/'));
        if (curDir.equals(targetDir)) {
            f.setStorageZone(targetZone == null ? "BUSINESS" : targetZone.toUpperCase());
            return f; // 已在该区域，幂等跳过
        }
        // 新路径：替换首段目录
        String newPath = targetDir + f.getFilePath().substring(f.getFilePath().indexOf('/'));
        Path src = Paths.get(localRoot).resolve(f.getFilePath()).normalize();
        Path dst = Paths.get(localRoot).resolve(newPath).normalize();
        try {
            if (Files.exists(src)) {
                Files.createDirectories(dst.getParent());
                Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "归档迁移文件失败: " + e.getMessage());
        }
        SysFile upd = new SysFile();
        upd.setId(f.getId());
        upd.setFilePath(newPath);
        upd.setStorageZone(targetZone == null ? "ARCHIVE" : targetZone.toUpperCase());
        fileMapper.updateById(upd);
        f.setFilePath(newPath);
        f.setStorageZone(targetZone == null ? "ARCHIVE" : targetZone.toUpperCase());
        return f;
    }

    // ---------- 工具 ----------

    /** 当前登录用户ID（可能为 null，仅用于留痕，不参与鉴权） */
    private Long userId() {
        var u = com.fssc.framework.security.UserContext.get();
        return u == null ? null : u.getUserId();
    }

    private String md5(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream in = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }
}
