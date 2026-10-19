// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.cache.TenantCache;
import com.fssc.framework.security.DataScopeHolder;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 主数据 Subject 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class SubjectService {

    /** 缓存域：科目全量列表（记账/映射配置高频只读，进程内 TTL 缓存） */
    private static final String DOMAIN_TREE = "subjectTree";

    /**
     * R5-D-043／R5-D-158 科目编码字符集（写侧 ASCII 校验）：只允许 ASCII 数字，层级分隔符允许
     * {@code .} {@code -} {@code _}，必须以数字开头，总长 ≤ 30。口径取自既有编码体系（种子脚本与
     * 凭证侧引用均为纯数字码，如 1002／1122／1601／160400000；只读全量核对：合法行 0 例外、存量非法
     * 编码 100% 为非 ASCII——265 行把中文名写进了编码列），用于拦掉"编码含非 ASCII 字符"这类脏值。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9][0-9._-]{0,29}");

    /** R5-D-158 科目类型取值域：键＝库内码值，值＝界面业务名称（报错只对外显业务名称） */
    private static final Map<String, String> SUBJECT_TYPES = new LinkedHashMap<>();

    static {
        SUBJECT_TYPES.put("ASSET", "资产");
        SUBJECT_TYPES.put("LIABILITY", "负债");
        SUBJECT_TYPES.put("EQUITY", "权益");
        SUBJECT_TYPES.put("REVENUE", "收入");
        SUBJECT_TYPES.put("COST", "费用");
    }

    private final MdSubjectMapper mapper;

    /**
     * R5-D-042（公司维度缺失·列表读路径）：原实现只有 tenant_id 隔离＋关键字，同租户内跨公司全量可见，
     * 页面无法判断某条科目属于哪家公司、也无法按公司筛。现与 {@code BankAccountService.page}（R5-D-026）
     * 同法接 data_scope 五级收口；本表无部门／经办人列，更窄的级别由 {@link DataScopeKit} 降级到本公司。
     *
     * <p>显式筛选与数据范围是 <b>AND 叠加</b>：选了公司只会更窄，绝不放宽到登录人范围之外。</p>
     */
    @DataScope("会计科目列表数据范围")
    public PageResult<MdSubject> page(long pageNum, long pageSize, String keyword, Long companyId) {
        LambdaQueryWrapper<MdSubject> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdSubject::getSubjectName, keyword).or().like(MdSubject::getSubjectCode, keyword));
        }
        applyCompanyDimension(qw, companyId);
        DataScopeKit.apply(qw, MdSubject::getCompanyId, null, null);
        qw.orderByDesc(MdSubject::getId);
        Page<MdSubject> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 旧三参签名保留（兼容仓内其它调用方；SubjectController 已绑 companyId 并入四参版）：不带显式公司筛选，仍受 data_scope 收口。 */
    @DataScope("会计科目列表数据范围")
    public PageResult<MdSubject> page(long pageNum, long pageSize, String keyword) {
        return page(pageNum, pageSize, keyword, null);
    }

    /**
     * 树形数据：按科目编码排序返回全量（前端按 parentId 组树）。P1-3：进程内 TTL 缓存，调用方只读。
     *
     * <p>R5-D-042：补公司维度。缓存键只到"租户"粒度，一旦本次查询带了公司筛选或登录人范围窄于本租户，
     * 继续复用该键就会把"别人范围的结果"喂给这个请求（宽范围先加载 ⇒ 窄范围读到全量＝跨公司外泄），
     * 故<b>只有"本租户全量且无显式筛选"这一档走缓存</b>，收窄口径一律回源直查。</p>
     */
    @DataScope("会计科目树数据范围")
    public java.util.List<MdSubject> tree() {
        return tree(null);
    }

    /**
     * @param companyId 显式公司筛选（null＝不按公司筛，仍按 data_scope 收口）
     */
    @DataScope("会计科目树数据范围")
    public java.util.List<MdSubject> tree(Long companyId) {
        if (isNarrowed(companyId)) {
            return mapper.selectList(newCompanyScoped(companyId));
        }
        return TenantCache.get(TenantCache.mdKey(DOMAIN_TREE), () -> mapper.selectList(
                new LambdaQueryWrapper<MdSubject>()
                        .orderByAsc(MdSubject::getSubjectCode).orderByAsc(MdSubject::getSubjectLevel)));
    }

    /**
     * 公司维度谓词（R5-D-042）。
     *
     * <p>md_subject 的 {@code company_id} 为空＝<b>租户级共用科目表</b>（见实体注释与库内
     * {@code uk_tenant_subject(tenant_id, company_id, subject_code)} 口径），所以"只看某公司"必须
     * 是「本公司科目 ∪ 租户共用科目」，否则共用科目表会在筛选后整棵消失。该并集只在调用人自己的
     * data_scope 之内取，不放宽范围；{@code md_period} 等 {@code company_id NOT NULL} 的表不适用此口径，
     * 各自用等值筛选。</p>
     */
    private static void applyCompanyDimension(LambdaQueryWrapper<MdSubject> qw, Long companyId) {
        if (companyId == null) {
            return;
        }
        qw.and(w -> w.eq(MdSubject::getCompanyId, companyId).or().isNull(MdSubject::getCompanyId));
    }

    /** 带公司维度（筛选＋data_scope 收口）的树查询；不走缓存，见 {@link #tree(Long)} 的键位说明。 */
    private LambdaQueryWrapper<MdSubject> newCompanyScoped(Long companyId) {
        LambdaQueryWrapper<MdSubject> qw = new LambdaQueryWrapper<>();
        applyCompanyDimension(qw, companyId);
        DataScopeKit.apply(qw, MdSubject::getCompanyId, null, null);
        return qw.orderByAsc(MdSubject::getSubjectCode).orderByAsc(MdSubject::getSubjectLevel);
    }

    /**
     * 本次查询是否比"本租户全量"更窄（显式选了公司，或登录人 data_scope 为 2~5 档）。
     * 载体为空＝{@code @DataScope} 未生效（如平台端直通），保守视为更窄，宁可不缓存也不共享键位。
     */
    private static boolean isNarrowed(Long companyId) {
        if (companyId != null) {
            return true;
        }
        DataScopeHolder.Scope s = DataScopeHolder.get();
        return s == null || s.getScope() > com.fssc.common.enums.DataScope.TENANT.getCode();
    }

    public Long create(MdSubject e) {
        // R5-D-043／R5-D-158：company_id 为空＝无归属行（存量 86 行 NULL 归属待人工体检回填，见人工回填单）。
        // 新增口禁止再产 NULL 归属：前端「新增」弹窗不携带该字段，先按登录主体回填（与 PeriodService 的
        // D-MD-PERIOD1 同法：UserContext 取不到再显式报错，文案为业务语义、不外泄内部码）。
        if (e.getCompanyId() == null) {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) {
                e.setCompanyId(lu.getCompanyId());
            }
        }
        AssertUtil.notNull(e.getCompanyId(), ErrorCode.PARAM_INVALID, "所属公司不能为空，请先选择公司主体后再新增科目");
        assertWritable(e, null);
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
        return e.getId();
    }

    public void update(MdSubject e) {
        AssertUtil.notNull(e.getId(), ErrorCode.PARAM_INVALID, "缺少科目标识，无法修改");
        assertWritable(e, e.getId());
        // company 归属不在本口处理（R5-D-043／R5-D-158）：updateById 只写非 null 字段 ⇒ payload 未带 companyId 时
        // 归属保持库值——租户共用行（company_id=NULL）的编辑不得被误回填成登录公司；回填/拒绝只发生在 create 口。
        mapper.updateById(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    public void enable(Long id, boolean enable) {
        MdSubject u = new MdSubject();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    // ---------------- R5-D-158 写侧守卫 ----------------

    /**
     * 科目新增/修改的落库前校验（只做"防新增脏值"，存量异常行由人工裁决回填，不在此处修数）。
     *
     * <p>五项：①编码字符集 ②名称非空 ③编码在本租户活跃行内唯一 ④科目类型属既有取值域
     * ⑤已有下级或已产生分录的科目不得被置为末级。文案一律业务语言，不外泄内部码值。</p>
     *
     * @param selfId 修改时的自身科目标识（新增传 null，用于唯一性判定排除自己）
     */
    private void assertWritable(MdSubject e, Long selfId) {
        // ① 编码字符集（先去空白，再判字符集，避免把"编码为空"报成"字符不合法"）
        String code = trimToNull(e.getSubjectCode());
        AssertUtil.notNull(code, ErrorCode.PARAM_INVALID, "请输入科目编码");
        AssertUtil.isTrue(CODE_PATTERN.matcher(code).matches(), ErrorCode.PARAM_INVALID,
                "科目编码「" + code + "」不合规：只能使用数字，可用小数点或短横线作层级分隔"
                        + "（示例：1002、1002.01），需以数字开头且不超过 30 位；"
                        + "若编码是从外部系统带入的，请先在源系统整理后再录入");
        e.setSubjectCode(code);

        // ② 名称：非空（拒空白串）且不得与编码同值（对应 R5-D-043 的 216 行空名与 46 行同值脏值；
        //    同值行全部落在 company_id IS NULL 行，"以码为名"会让界面与凭证取数失去可读性）
        String name = trimToNull(e.getSubjectName());
        AssertUtil.notNull(name, ErrorCode.PARAM_INVALID, "请输入科目名称");
        AssertUtil.isTrue(!name.equals(code), ErrorCode.PARAM_INVALID,
                "科目名称不能与科目编码相同（当前均为「" + code + "」），请填写可识别的科目名称");
        e.setSubjectName(name);

        // ③ 编码唯一（走 assertCodeUnique，与编码查重族同法；排除自身，软删行由 @TableLogic 自动排除）
        assertCodeUnique(code, selfId);

        // ④ 科目类型取值域
        String type = trimToNull(e.getSubjectType());
        AssertUtil.notNull(type, ErrorCode.PARAM_INVALID,
                "请选择科目类型（" + String.join("／", SUBJECT_TYPES.values()) + "）");
        String normalized = type.toUpperCase();
        AssertUtil.isTrue(SUBJECT_TYPES.containsKey(normalized), ErrorCode.PARAM_INVALID,
                "科目类型「" + type + "」不在可选范围内，请从 "
                        + String.join("／", SUBJECT_TYPES.values()) + " 中选择");
        e.setSubjectType(normalized);

        // ⑤ 末级判定守卫
        if (selfId != null && e.getIsLeaf() != null && e.getIsLeaf() == 1) {
            long children = mapper.countChildren(selfId);
            AssertUtil.isTrue(children == 0, ErrorCode.PARAM_INVALID,
                    "科目「" + name + "」（编码 " + code + "）下还挂着 " + children
                            + " 个下级科目，不能置为末级科目；请先调整或删除下级科目");
            long voucherLines = mapper.countVoucherLines(selfId);
            long glEntries = mapper.countGlEntries(selfId);
            AssertUtil.isTrue(voucherLines == 0 && glEntries == 0, ErrorCode.PARAM_INVALID,
                    "科目「" + name + "」（编码 " + code + "）已产生 " + (voucherLines + glEntries)
                            + " 条记账分录，不能置为末级科目；末级与否请结合余额方向与辅助核算重新确认后保持现状");
        }
    }

    /**
     * 编码唯一（不含自身）：与波次十三/十四「编码查重族」（SupplierService／ExpenseStandardService 的
     * assertCodeUnique）同方法命名、同异常语义——先查重给业务提示，不等 SQL 唯一键报 SQL 错。
     * 判定范围＝本租户活跃行（软删行由 {@code @TableLogic} 自动排除、他租户由租户拦截器排除）。
     */
    private void assertCodeUnique(String code, Long selfId) {
        Long dup = mapper.selectCount(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, code)
                .ne(selfId != null, MdSubject::getId, selfId));
        AssertUtil.isTrue(dup == null || dup == 0, ErrorCode.PARAM_INVALID,
                "科目编码「" + code + "」已存在，请换一个编码；如需调整现有科目，请直接修改那条科目而不是新建");
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
