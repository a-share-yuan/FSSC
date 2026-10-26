// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.document.entity.ArchiveAttachment;
import com.fssc.document.entity.OpArchive;
import com.fssc.document.mapper.ArchiveAttachmentMapper;
import com.fssc.system.entity.SysFile;
import com.fssc.system.mapper.SysFileMapper;
import com.fssc.framework.datasource.FsscDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 档案完整性快照哈希（D-DEC-180，P1，证据 12-31:158）。
 *
 * <p><b>本类是哈希算式的唯一权威</b>：写入侧（{@code ArchiveService.archive/autoArchive}、
 * {@code BillArchiveService.doArchive}）与校验/对账读侧（{@code verifyIntegrity}、
 * {@code verifyIntegrityFull}、{@code preArchiveFailures}）一律调本方法，
 * 不允许再出现第二套算式。
 *
 * <p><b>D-DEC-180 的三条根因（本类逐条对症）</b>：
 * <ol>
 *   <li><b>两套算式并存 ⇒ 同单异值（误报）</b>：整改前 {@code ArchiveService} 里有
 *       {@code computeIntegrityHash(archiveNo|refId|archiveType|fileId+文件MD5)} 与
 *       {@code calculateIntegrityHash(archiveCode+archiveType+billId+voucherId+附件清单)} 两个私有方法，
 *       前者被「执行归档／校验／定稿校验」用，后者被「自动归档／verifyFull」用；
 *       {@code BillArchiveService.doArchive} 更省事，直接把<b>主文件的裸 MD5</b> 当哈希写进
 *       {@code integrity_hash}（第三种算式）。于是同一卷档案经不同入口得到三个值，
 *       校验时按其中一种重算必然与另一种不符 ⇒ 界面报「可能被篡改」。
 *       实测证据：{@code bill_id=22} 的两份档案哈希互异（id=12 {@code 101ccb14…} vs id=24 {@code bc0a1eb a…}）。
 *   </li>
 *   <li><b>输入集不含区分要素 ⇒ 异卷同值（漏报）</b>：{@code calculateIntegrityHash} 的输入是
 *       {@code archive_code|archive_type|bill_id|voucher_id|附件清单}，而现网
 *       ① {@code archive_code} 大面积 NULL（D-DEC-184）、② 由时间戳后 5 位生成（100 秒内必重码）、
 *       ③ {@code archive_attachment} 库里 0 行（实测 COUNT=0）⇒ 同类型的两卷档案输入完全相同，
 *       哈希必然相等，「内容被换掉」根本检不出来。
 *       实测证据：id=21（BX202609250004）与 id=23（BX202609250005）<b>两张不同档案同一哈希</b>
 *       {@code 09d09451e40804b938edaa64903dee3c}。
 *   </li>
 *   <li><b>输入集含非确定因子</b>：整改前的算法在部分入口把创建时刻/公司归属卷进输入，
 *       同一卷重算随时间漂移。本算式<b>只取不可变要素</b>，杜绝漂移。</li>
 * </ol>
 *
 * <p><b>v2 算式（确定性 + 可区分 + 内容可验）</b>，串接顺序固定、空值以空串占位：
 * <pre>
 *   ARCHIVE-HASH-V2|id=档案主键|no=档案编号|code=档案编码|type=档案类型|
 *   ref=ref_id|bill=bill_id|vch=voucher_id|refno=关联单号|year=档案年度|period=档案期间|
 *   files=[归档载体清单：fileId:MD5，按 fileId 升序，去重]，files 段前给出条数|
 *   att=[archive_attachment 清单：fileName|contentHash，按字典序，去重]＋条数
 * </pre>
 * 三点取舍：
 * <ul>
 *   <li><b>档案主键入算</b>：不同卷永不再同值（漏报治愈）。哈希的对象是<b>这一卷档案的归档快照</b>，
 *       不是「这张单据的内容」——同一单据的两卷档案本就该有两个快照值，
 *       与 {@code id=12}/{@code id=24} 那组「误报」是两回事（那组的病因是算式分叉，已治愈）。</li>
 *   <li><b>不进算式的列</b>：一切时间列（{@code filing_time/archived_at/create_time/update_time/destroy_date}）、
 *       状态列（{@code archive_status/status/integrity_status}）、归属列（{@code company_id/tenant_id}）
 *       与文案列（{@code title/remark}）。理由＝它们要么被后续环节改写
 *       （{@code archive_status/integrity_status/archived_at} 由 FM_ARCHIVE 审批闭环写、
 *       {@code filing_user_id/filing_time} 由 {@code ArchiveFacadeImpl.handleArchiveApproval} 默认处理补写），
 *       进算式就会把「正常回写」算成「篡改」；要么与内容无关。
 *       归属/标题若要改，走 {@code D-DEC-184} 的建档补齐（在 insert 之前），不影响已固化快照。</li>
 *   <li><b>清单排序后入算</b>：{@code related_ids} 的存储顺序取决于查询顺序（附件表/影像表 join），
 *       不排序就会出现「内容没变、顺序变了 ⇒ 哈希变了」的假阳性，故一律升序去重。</li>
 * </ul>
 *
 * <p><b>版本前缀的作用</b>：v2 串里带 {@code ARCHIVE-HASH-V2}，历史行的旧哈希（含裸文件 MD5）
 * 与新值必然不符 ⇒ 重算前先 {@code MISMATCH} 是<b>预期行为</b>而不是新缺陷。
 * 历史重算属数据轨（需一次 Java 窗口按卷调 {@link #snapshot(OpArchive)} 回灌，SQL 无法算 MD5 串），
 * 本轮不执行、不改写历史行（演练数据保留铁律）。
 *
 * <p>查询失败（sys_file 查不到／附件表异常）不抛异常——完整性哈希是附加能力，
 * 档案主链路（立卷/审批/检索）不得因它中断；此时以占位段 {@code file:<id>:no-md5} 入算，
 * 保证同一卷重算结果稳定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("document")
public class ArchiveIntegrityService {

    /** 算式版本前缀（入哈希串，使旧算式产出的哈希与新值不可能巧合相等） */
    public static final String HASH_VERSION = "ARCHIVE-HASH-V2";

    private final SysFileMapper sysFileMapper;
    private final ArchiveAttachmentMapper archiveAttachmentMapper;

    /**
     * 计算一卷档案的完整性快照哈希。
     *
     * @param a 档案行（须已带 {@code id}；未入库的行先 insert 再算，见 {@code BillArchiveService.doArchive}）
     * @return 32 位 MD5 十六进制串；{@code a} 为 null 时返回 null（不冒充校验能力）
     */
    public String snapshot(OpArchive a) {
        if (a == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(256).append(HASH_VERSION);
        seg(sb, "id", a.getId());
        seg(sb, "no", a.getArchiveNo());
        seg(sb, "code", a.getArchiveCode());
        seg(sb, "type", a.getArchiveType());
        seg(sb, "ref", a.getRefId());
        seg(sb, "bill", a.getBillId());
        seg(sb, "vch", a.getVoucherId());
        seg(sb, "refno", a.getRefNo());
        seg(sb, "year", a.getArchiveYear());
        seg(sb, "period", a.getArchivePeriod());
        // —— 内容清单 1：归档载体（file_id 主件 + related_ids 关联件），按 fileId 升序去重
        List<String> files = fileDigests(a);
        sb.append("|files=").append(files.size()).append(':').append(String.join(",", files));
        // —— 内容清单 2：档案附件登记（archive_attachment），字典序去重
        List<String> atts = attachmentDigests(a.getId());
        sb.append("|att=").append(atts.size()).append(':').append(String.join(",", atts));
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 固定 {@code |key=value} 片段：null/空白一律落成空串，避免 "null" 字面量参与哈希 */
    private static void seg(StringBuilder sb, String key, Object v) {
        sb.append('|').append(key).append('=')
                .append(v == null || "null".equals(String.valueOf(v)) ? "" : String.valueOf(v).trim());
    }

    /**
     * 归档载体清单：主件 {@code file_id} ＋ 关联件 {@code related_ids}（逗号串/JSON 数组串均可解析），
     * 每项取 {@code sys_file.file_md5}；文件不存在或无 MD5 时以 {@code file:<id>:no-md5} 占位
     * （占位也是确定的，重算仍自洽）。
     */
    private List<String> fileDigests(OpArchive a) {
        TreeSet<String> entries = new TreeSet<>();
        List<Long> ids = new ArrayList<>();
        if (a.getFileId() != null) {
            ids.add(a.getFileId());
        }
        for (Long id : parseIdList(a.getRelatedIds())) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            String md5 = null;
            try {
                SysFile f = sysFileMapper.selectById(id);
                if (f != null) {
                    md5 = f.getFileMd5();
                }
                if (md5 == null || md5.isBlank()) {
                    log.debug("档案 {} 的载体文件 {} 无 MD5，完整性清单以占位入算", a.getId(), id);
                }
            } catch (Exception ex) {
                log.warn("档案 {} 取载体文件 {} 失败（以占位入算，不阻断归档）: {}", a.getId(), id, ex.getMessage());
            }
            entries.add(id + ":" + (md5 == null || md5.isBlank() ? "no-md5" : md5.trim()));
        }
        return new ArrayList<>(entries);
    }

    /** 档案附件登记清单（{@code archive_attachment} 的 fileName + contentHash），字典序去重 */
    private List<String> attachmentDigests(Long archiveId) {
        TreeSet<String> entries = new TreeSet<>();
        if (archiveId == null) {
            return new ArrayList<>();
        }
        try {
            List<ArchiveAttachment> rows = archiveAttachmentMapper.selectList(
                    new LambdaQueryWrapper<ArchiveAttachment>()
                            .eq(ArchiveAttachment::getArchiveId, archiveId));
            for (ArchiveAttachment t : rows) {
                entries.add((t.getFileName() == null ? "" : t.getFileName().trim())
                        + "|" + (t.getContentHash() == null ? "" : t.getContentHash().trim()));
            }
        } catch (Exception ex) {
            log.warn("档案 {} 取附件清单失败（以已取到的部分入算）: {}", archiveId, ex.getMessage());
        }
        return new ArrayList<>(entries);
    }

    /** {@code related_ids} 解析：兼容 CSV／"[1,2]"／带引号形态，非数字段忽略 */
    static List<Long> parseIdList(String raw) {
        List<Long> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.replaceAll("[\\[\\]\"\\s]", "").split("[,;]")) {
            if (part.isEmpty() || !part.matches("\\d+")) {
                continue;
            }
            try {
                out.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // 超长数字串＝不是本域主键，跳过
            }
        }
        return out;
    }
}
