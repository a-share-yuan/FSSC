package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysDictData;
import com.fssc.system.entity.SysDictType;
import com.fssc.system.mapper.SysDictDataMapper;
import com.fssc.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 租户字典管理（sys_dict_type / sys_dict_data，均带 tenant_id） */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;

    // ---------- 字典类型 ----------

    public PageResult<SysDictType> typePage(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysDictType> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysDictType::getDictType, keyword)
                    .or().like(SysDictType::getDictName, keyword));
        }
        qw.orderByDesc(SysDictType::getId);
        Page<SysDictType> p = sysDictTypeMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long typeCreate(SysDictType type) {
        // 必填收口（批次 2C，与前端 GenericList 字典类型弹窗同口径）：字典编码是取值键、名称是展示键
        AssertUtil.notBlank(type.getDictName(), ErrorCode.PARAM_INVALID, "请输入字典名");
        AssertUtil.notBlank(type.getDictType(), ErrorCode.PARAM_INVALID, "请输入字典编码");
        long dup = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, type.getDictType()));
        if (dup > 0) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "字典类型已存在: " + type.getDictType());
        }
        type.setId(null);
        sysDictTypeMapper.insert(type);
        return type.getId();
    }

    public void typeUpdate(SysDictType type) {
        // 整单更新（前端提交完整对象），故与 typeCreate 同口径拦空
        AssertUtil.notBlank(type.getDictName(), ErrorCode.PARAM_INVALID, "请输入字典名");
        AssertUtil.notBlank(type.getDictType(), ErrorCode.PARAM_INVALID, "请输入字典编码");
        sysDictTypeMapper.updateById(type);
    }

    /** 删除字典类型（逻辑删；有字典数据时拒绝） */
    public void typeDelete(Long id) {
        SysDictType type = sysDictTypeMapper.selectById(id);
        if (type != null) {
            long cnt = sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, type.getDictType()));
            if (cnt > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该字典类型下存在 " + cnt + " 条字典数据，不能删除");
            }
        }
        sysDictTypeMapper.hardDelete(id);
    }

    // ---------- 字典数据 ----------

    public List<SysDictData> dataList(String dictType) {
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getSort));
    }

    // ---------- 内置字典（免权限只读通道的闸门） ----------

    /**
     * 当前租户的「内置字典类型」编码集合（is_builtin=1 且 status=NORMAL），按租户缓存 5 分钟。
     *
     * 缓存 key 用 {@code dictbuiltin:} 前缀，与 {@code dict:} 前缀分离，
     * 但**字典任何写操作都会同时失效这两个前缀**（见 TenantDictController#evictDictCache），
     * 故新增/改内置标记后不会长时间读到旧集合。
     */
    private Set<String> builtinTypes() {
        Long tid = com.fssc.framework.tenant.TenantContext.getTenantId();
        return com.fssc.framework.cache.CacheUtil.get("dictbuiltin:" + tid, () ->
                sysDictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getIsBuiltin, 1)
                        .eq(SysDictType::getStatus, "NORMAL"))
                        .stream().map(SysDictType::getDictType).collect(Collectors.toSet()));
    }

    /**
     * 【2026-10-15 枚举收口前置A】内置字典数据（供免权限端点调用）。
     *
     * 与 {@link #dataList(String)} 的唯一差别：**先过 is_builtin 闸门**。
     * 非内置（租户自定义）字典一律返回空列表 —— 因为该端点不带任何权限码，
     * 任何登录用户都能调，绝不能成为"绕过 system:dict:list 偷看租户自定义配置"的侧门。
     *
     *  越权请求**不抛异常**：返回空即可让调用方自然回落本地常量（fail-soft），
     *    抛错反而会在每个业务页面刷 400 日志。这里只记一条 warn 便于排查。
     *
     * @return 内置字典的数据行；非内置/不存在 ⇒ 空列表（绝不返回租户自定义字典）
     */
    public List<SysDictData> builtinDataList(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        Set<String> allow = builtinTypes();
        if (!allow.contains(dictType)) {
            log.warn("[dict] 拒绝非内置字典读取请求 dictType={} tenantId={}（该端点免权限，仅暴露 is_builtin=1 的类型）",
                    dictType, com.fssc.framework.tenant.TenantContext.getTenantId());
            return List.of();
        }
        return dataList(dictType);
    }

    /**
     * 【2026-10-16 第 4 批】批量读取内置字典（供前端一次性预热 66 个 enum_* 展示字典）。
     *
     * 为什么需要它：枚举收口后前端有 67 个展示字典，若逐个调
     * {@code /builtin/data/list} 就要 67 次往返 —— 登录页首屏扛不住，故合并成一次。
     *
     * 安全性与 {@link #builtinDataList(String)} **同一道闸门**：只返回 is_builtin=1 的类型。
     * 差别只在失败语义：单个请求里夹带的非内置类型**静默剔除**（记 warn），
     * 而不是整批抛异常 —— 预热是启动期批量动作，不能因一个脏类型让整批字典失效。
     *
     * @param dictTypes 请求的字典类型；返回 Map 只含通过闸门的类型（未通过的不出现在 key 里）
     */
    public Map<String, List<SysDictData>> builtinDataBatch(Collection<String> dictTypes) {
        if (dictTypes == null || dictTypes.isEmpty()) {
            return Map.of();
        }
        Set<String> req = dictTypes.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (req.isEmpty()) {
            return Map.of();
        }
        Set<String> allow = builtinTypes();
        List<String> ok = req.stream().filter(allow::contains).toList();
        List<String> denied = req.stream().filter(t -> !allow.contains(t)).toList();
        if (!denied.isEmpty()) {
            log.warn("[dict] 批量内置字典读取剔除非内置类型 denied={} tenantId={}（该端点免权限，仅暴露 is_builtin=1 的类型）",
                    denied, com.fssc.framework.tenant.TenantContext.getTenantId());
        }
        if (ok.isEmpty()) {
            return Map.of();
        }
        // 一次查完再按 dict_type 归组，避免 N 次单表查询
        List<SysDictData> rows = sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .in(SysDictData::getDictType, ok)
                .orderByAsc(SysDictData::getDictType)
                .orderByAsc(SysDictData::getSort));
        Map<String, List<SysDictData>> map = new LinkedHashMap<>();
        for (String t : ok) {
            map.put(t, new ArrayList<>());
        }
        for (SysDictData d : rows) {
            List<SysDictData> bucket = map.get(d.getDictType());
            if (bucket != null) {
                bucket.add(d);
            }
        }
        return map;
    }

    public Long dataCreate(SysDictData data) {
        validateData(data);
        data.setId(null);
        sysDictDataMapper.insert(data);
        return data.getId();
    }

    public void dataUpdate(SysDictData data) {
        validateData(data);
        sysDictDataMapper.updateById(data);
    }

    /** 必填收口（批次 2C，与前端 GenericList 字典数据项弹窗 dataItemRules 同口径） */
    private void validateData(SysDictData data) {
        AssertUtil.notBlank(data.getDictType(), ErrorCode.PARAM_INVALID, "缺少字典编码");
        AssertUtil.notBlank(data.getDictLabel(), ErrorCode.PARAM_INVALID, "请填写标签");
        AssertUtil.notBlank(data.getDictValue(), ErrorCode.PARAM_INVALID, "请填写值");
    }

    public void dataDelete(Long id) {
        // 逻辑删除（@TableLogic）
        sysDictDataMapper.hardDelete(id);
    }
}
