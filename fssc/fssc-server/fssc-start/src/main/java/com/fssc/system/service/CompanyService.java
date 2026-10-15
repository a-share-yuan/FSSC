package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysCompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 公司（纳税主体）管理。集团型租户多法人公司，X-Company-Id 切换受租户归属校验约束。 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final SysCompanyMapper sysCompanyMapper;

    public List<SysCompany> tree() {
        return sysCompanyMapper.selectList(new LambdaQueryWrapper<SysCompany>()
                .orderByAsc(SysCompany::getAncestors).orderByAsc(SysCompany::getSort));
    }

    public Long create(SysCompany company) {
        // 必填收口（批次 2C，与前端 Org.vue「公司名称/公司编码」同口径）：名称与编码是人工维护的业务键
        AssertUtil.notBlank(company.getCompanyName(), ErrorCode.PARAM_INVALID, "请输入公司名称");
        AssertUtil.notBlank(company.getCompanyCode(), ErrorCode.PARAM_INVALID, "请输入公司编码");
        Long dup = countByCode(company.getCompanyCode());
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公司编码已存在: " + company.getCompanyCode());
        }
        company.setId(null);
        company.setAncestors(calcAncestors(company.getParentId()));
        sysCompanyMapper.insert(company);
        return company.getId();
    }

    public void update(SysCompany company) {
        SysCompany exist = sysCompanyMapper.selectById(company.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公司不存在: " + company.getId());
        }
        // 整单更新（前端提交完整对象），故与 create 同口径拦空；enable() 走独立对象不受影响
        AssertUtil.notBlank(company.getCompanyName(), ErrorCode.PARAM_INVALID, "请输入公司名称");
        AssertUtil.notBlank(company.getCompanyCode(), ErrorCode.PARAM_INVALID, "请输入公司编码");
        sysCompanyMapper.updateById(company);
    }

    public void enable(Long id, boolean enable) {
        SysCompany u = new SysCompany();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        sysCompanyMapper.updateById(u);
    }

    /** 删除公司（逻辑删；有子公司时拒绝） */
    public void delete(Long id) {
        long sub = sysCompanyMapper.selectCount(new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getParentId, id));
        if (sub > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在下级公司，不能删除");
        }
        sysCompanyMapper.hardDelete(id);
    }

    private long countByCode(String code) {
        return sysCompanyMapper.selectCount(new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getCompanyCode, code));
    }

    /** 祖级路径：父为 0 → "0"；否则 父.ancestors + "," + 父.id */
    private String calcAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        SysCompany p = sysCompanyMapper.selectById(parentId);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上级公司不存在: " + parentId);
        }
        String a = p.getAncestors() == null || p.getAncestors().isBlank() ? "0" : p.getAncestors();
        return a + "," + parentId;
    }
}
