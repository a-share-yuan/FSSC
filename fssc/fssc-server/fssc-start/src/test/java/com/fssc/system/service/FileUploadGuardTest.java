// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysFile;
import com.fssc.system.mapper.SysFileMapper;
import com.fssc.system.mapper.SysFileTokenMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-11（04 册「上传白名单须改码」FSSC 腿）回归单测：{@link FileService#upload} 的三道闸。
 *
 * <ul>
 *   <li><b>下限闸</b>：{@code fssc.file.allowed-extensions} 被配置成空值时白名单不校验（fail-open），
 *       此时脚本/网页/可执行一类后缀仍必须被 {@code FORBIDDEN_SUFFIXES} 拒掉；</li>
 *   <li><b>落盘改名</b>：磁盘名 = 随机 UUID + 净化后缀（只留 [a-z0-9]、至多 8 位），
 *       原始名里的 {@code ../}、{@code .jsp.} 之类线索既进不了文件名也进不了目录；</li>
 *   <li><b>拒绝文案</b>：给用户的 {@code BizException} 文案不回显配置原文与内部清单。</li>
 * </ul>
 *
 * <p>不用 Mockito（JDK 26 下 byte-buddy 自附加受限，与本仓 {@code PermissionAspectTest} 同一取舍），
 * Mapper 用 JDK 动态代理伪造；不启动 Spring 上下文、不连数据库。</p>
 */
class FileUploadGuardTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 fake body".getBytes();

    private final List<SysFile> inserted = new ArrayList<>();

    /** 落盘根目录：每个用例换一个，断言"被拒即一个字节都不落盘"时用文件数说话 */
    @TempDir
    private Path root;

    private FileService service;

    @BeforeEach
    void setUp() {
        // FileService#upload 走 TenantContext.getTenantId()（fail-closed：无上下文即抛），单测先建上下文
        TenantContext.set(1L);
        inserted.clear();
        service = newService("", root);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** allowedExtensions 传空串 = 复现"白名单被外部清空"的 fail-open 配置 */
    private FileService newService(String allowedExtensions, Path storageRoot) {
        FileService svc = new FileService(mapper(SysFileMapper.class, inserted),
                (SysFileTokenMapper) proxy(SysFileTokenMapper.class, (p, method, args) -> null));
        ReflectionTestUtils.setField(svc, "localRoot", storageRoot.toString());
        ReflectionTestUtils.setField(svc, "maxFileSize", 20_971_520L);
        ReflectionTestUtils.setField(svc, "allowedExtensions", allowedExtensions);
        ReflectionTestUtils.setField(svc, "zoneTempDir", "temp");
        ReflectionTestUtils.setField(svc, "zoneBusinessDir", "business");
        ReflectionTestUtils.setField(svc, "zoneArchiveDir", "archive");
        return svc;
    }

    // 下限闸：不依赖配置

    @Test
    @DisplayName("C-11：白名单被清空时，脚本/网页/可执行后缀仍被下限表拒绝，且一个字节都不落盘")
    void dangerousSuffix_rejectedEvenWhenWhitelistBlank() throws IOException {
        for (String name : List.of("shell.jsp", "page.html", "tool.exe", "invoice.svg",
                "report.docm", "runner.ps1", "app.jar")) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.upload(multipart(name), "CLAIM", 1L, "BUSINESS"),
                    "必须拒绝：" + name);
            assertNotNull(ex.getMessage());
        }
        assertEquals(0, inserted.size(), "被拒的上传不能写 sys_file");
        assertEquals(0, diskFiles().size(), "被拒的上传不能落盘");
    }

    @Test
    @DisplayName("C-11：a.pdf/../evil.jsp 这类复合名——取最后一段后缀，命中下限表即拒")
    void doubleExtension_rejectedByFloor() {
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(multipart("a.pdf/../evil.jsp"), "CLAIM", 1L, "BUSINESS"));
        assertFalse(ex.getMessage().contains("evil"), "文案不得回显客户端给的名字");
        assertEquals(0, diskFiles().size());
    }

    // 落盘改名与目录闸

    @Test
    @DisplayName("C-11：磁盘名只有 UUID+后缀，不含原始文件名；展示名仍保留原始名")
    void storedNameCarriesNoOriginalFileName() throws IOException {
        SysFile saved = service.upload(multipart("银行回单 2026-09.pdf"), "CLAIM", 88L, "BUSINESS");
        assertEquals(1, inserted.size(), "落库一次");
        Path onDisk = root.resolve(saved.getFilePath()).normalize();
        assertTrue(onDisk.startsWith(root.toAbsolutePath().normalize()), "落盘必须仍在 local-root 内");
        assertTrue(Files.exists(onDisk), "必须真实落盘");
        String diskName = onDisk.getFileName().toString();
        assertEquals(32 + ".pdf".length(), diskName.length(), "磁盘名 = 32 位无横线 UUID + .pdf，实际=" + diskName);
        assertTrue(diskName.matches("[0-9a-f]{32}\\.pdf"), "磁盘名不得含原始名，实际=" + diskName);
        assertEquals("银行回单 2026-09.pdf", saved.getFileName(), "展示名保留原始名（下载 Content-Disposition 用）");
        assertEquals("pdf", saved.getFileSuffix());
        assertFalse(saved.getFilePath().contains("回单"), "路径里不能出现原始名");
    }

    @Test
    @DisplayName("C-11：bizType 含 ../ 时目录段被净化，文件仍落在 local-root 之内")
    void traversalInBizType_cannotEscapeStorageRoot() throws IOException {
        SysFile saved = service.upload(multipart("x.pdf"), "../../escape", 1L, "BUSINESS");
        Path onDisk = root.resolve(saved.getFilePath()).normalize();
        assertTrue(onDisk.startsWith(root.toAbsolutePath().normalize()),
                "越界落盘必须被拦：path=" + saved.getFilePath());
        assertTrue(saved.getFilePath().contains("/escape/"), "非法字符剥掉后仍可归档，实际=" + saved.getFilePath());
        assertEquals("../../escape", saved.getBizType(), "业务口径值不被净化改写（净化只作用于目录段）");
        assertEquals(1, diskFiles().size());
    }

    @Test
    @DisplayName("C-11：大写后缀照常放行（净化不影响合法格式判定）")
    void upperCaseSuffix_stillWorks() {
        SysFile saved = service.upload(multipart("INVOICE.PDF"), "CLAIM", 1L, "BUSINESS");
        assertEquals("pdf", saved.getFileSuffix());
    }

    // 白名单闸与拒绝文案

    @Test
    @DisplayName("C-11：白名单生效时清单外格式被拒，且文案不含配置原文")
    void notInWhitelist_rejectedWithoutEchoingConfig() {
        FileService strict = newService("pdf,png", root);
        BizException ex = assertThrows(BizException.class,
                () -> strict.upload(multipart("note.docx"), "CLAIM", 1L, "BUSINESS"));
        String msg = ex.getMessage();
        assertFalse(msg.contains("pdf,png"), "不得回显白名单配置原文，实际=" + msg);
        assertFalse(msg.contains("docx"), "不得回显内部判定细节，实际=" + msg);
        assertTrue(msg.contains("格式"), "文案要让用户知道下一步做什么，实际=" + msg);
        assertEquals(0, diskFiles().size());
    }

    @Test
    @DisplayName("C-11：白名单内格式正常放行（闸不改变既有业务口径）")
    void whitelistedFormat_passes() {
        FileService strict = newService("pdf,png", root);
        SysFile saved = strict.upload(multipart("claim.pdf"), "CLAIM", 1L, "BUSINESS");
        assertEquals("pdf", saved.getFileSuffix());
        assertEquals(1, diskFiles().size());
    }

    // 体积闸（回归，未被本次改动破坏）

    @Test
    @DisplayName("C-11：体积闸保持原口径（超限即拒，先于类型判定）")
    void oversize_rejected() {
        FileService tiny = newService("", root);
        ReflectionTestUtils.setField(tiny, "maxFileSize", 8L);
        assertThrows(BizException.class, () -> tiny.upload(multipart("x.pdf"), "CLAIM", 1L, "BUSINESS"));
    }

    // 辅助

    private static MockMultipartFile multipart(String originalName) {
        return new MockMultipartFile("file", originalName, "application/pdf", PDF_BYTES);
    }

    /** local-root 下已存在的文件清单（用于断言"拒了就是一个字节都没落盘"） */
    private List<String> diskFiles() {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).map(p -> root.relativize(p).toString()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 记录 insert() 入参的 SysFileMapper 伪实现 */
    private static SysFileMapper mapper(Class<SysFileMapper> iface, List<SysFile> sink) {
        return (SysFileMapper) proxy(iface, (p, method, args) -> {
            if ("insert".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof SysFile f) {
                sink.add(f);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                (p, method, args) -> {
                    if ("toString".equals(method.getName())) return iface.getSimpleName() + "$Stub";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(p);
                    if ("equals".equals(method.getName())) return p == args[0];
                    return handler.invoke(p, method, args);
                });
    }

    /** 伪 Mapper 的其它方法（selectById/updateById…）返回类型默认值：本组用例只关心 insert 有没有被调 */
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == int.class || type == short.class || type == byte.class) return 0;
        return null;
    }
}
