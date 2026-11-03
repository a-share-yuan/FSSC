// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.entity.TenantAuditBase;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.mapper.*;
import com.fssc.masterdata.entity.MdFormConfig;
import com.fssc.masterdata.entity.MdLoanLimit;
import com.fssc.masterdata.entity.MdLoanInitial;
import com.fssc.masterdata.entity.MdOrgType;
import com.fssc.masterdata.entity.MdCashFlow;
import com.fssc.masterdata.entity.MdItemCashMap;
import com.fssc.masterdata.entity.MdItemBudgetMap;
import com.fssc.masterdata.entity.MdCostCenterMap;
import com.fssc.masterdata.entity.MdBank;
import com.fssc.masterdata.entity.MdEmployeeLevel;
import com.fssc.masterdata.entity.MdGlConfig;
import com.fssc.masterdata.entity.MdIncomeExpense;
import com.fssc.masterdata.entity.MdBudgetCategory;
import com.fssc.masterdata.entity.MdBudgetOrg;
import com.fssc.masterdata.entity.MdContractType;
import com.fssc.masterdata.entity.MdBudgetPeriod;
import com.fssc.masterdata.entity.MdLoanPayType;
import com.fssc.masterdata.entity.MdVehicle;
import com.fssc.masterdata.entity.MdCip;
import com.fssc.masterdata.entity.MdCipCost;
import com.fssc.masterdata.dto.MdCipTransferRequest;
import com.fssc.masterdata.service.MdCipService;
import com.fssc.masterdata.entity.MdBudgetType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 基础数据-配置类 CRUD（/2.27~2.34）。
 * 统一路径 /api/tenant/masterdata/config/{type}/...，type 见各端点。
 */
@RestController
@RequestMapping("/api/tenant/masterdata/config")
@RequiredArgsConstructor
public class MasterConfigController {

    private final MdFormConfigMapper mdformconfigMapper;

    private final MdLoanLimitMapper mdloanlimitMapper;

    private final MdLoanInitialMapper mdloaninitialMapper;

    private final MdOrgTypeMapper mdorgtypeMapper;

    private final MdCashFlowMapper mdcashflowMapper;

    private final MdItemCashMapMapper mditemcashmapMapper;

    private final MdItemBudgetMapMapper mditembudgetmapMapper;

    private final MdCostCenterMapMapper mdcostcentermapMapper;

    private final MdVehicleMapper mdvehicleMapper;
    private final MdBankMapper mdbankMapper;
    private final MdEmployeeLevelMapper mdemployeelevelMapper;
    private final MdGlConfigMapper mdglconfigMapper;
    private final MdIncomeExpenseMapper mdincomeexpenseMapper;
    private final MdLoanPayTypeMapper mdloanpaytypeMapper;
    private final MdBudgetCategoryMapper mdbudgetcategoryMapper;
    private final MdBudgetOrgMapper mdbudgetorgMapper;
    private final MdBudgetPeriodMapper mdbudgetperiodMapper;
    private final MdContractTypeMapper mdcontracttypeMapper;
    private final MdCipMapper mdcipMapper;
    private final MdCipCostMapper mdcipcostMapper;
    private final MdCipService mdcipService;
    private final MdBudgetTypeMapper mdbudgettypeMapper;


    // 表单详情配置

    @RequiresPerm("md:form:list")
    @GetMapping("/form/page")
    public Result<PageResult<MdFormConfig>> formconfigPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdFormConfig> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdFormConfig::getRemark, keyword));
        }
        w.orderByDesc(MdFormConfig::getId);
        Page<MdFormConfig> p = mdformconfigMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:form:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增表单详情配置")
    @PostMapping("/form/create")
    public Result<Long> formconfigCreate(@RequestBody MdFormConfig entity) {
        mdformconfigMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:form:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新表单详情配置")
    @PostMapping("/form/update")
    public Result<Void> formconfigUpdate(@RequestBody MdFormConfig entity) {
        mdformconfigMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:form:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除表单详情配置")
    @PostMapping("/form/{id}/delete")
    public Result<Void> formconfigDelete(@PathVariable Long id) {
        mdformconfigMapper.deleteById(id);
        return Result.success();
    }

    /** 启用中的单据类型列表（发起入口/流程绑定动态化使用） */
    @RequiresPerm("md:form:list")
    @GetMapping("/form/options")
    public Result<List<MdFormConfig>> formOptions() {
        List<MdFormConfig> list = mdformconfigMapper.selectList(
                new LambdaQueryWrapper<MdFormConfig>()
                        .eq(MdFormConfig::getStatus, "NORMAL")
                        .orderByAsc(MdFormConfig::getBillType).orderByAsc(MdFormConfig::getBillSubType));
        return Result.success(list);
    }


    // 借款额度

    @RequiresPerm("md:loanlimit:list")
    @GetMapping("/loanLimit/page")
    public Result<PageResult<MdLoanLimit>> loanlimitPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdLoanLimit> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdLoanLimit::getRemark, keyword));
        }
        w.orderByDesc(MdLoanLimit::getId);
        Page<MdLoanLimit> p = mdloanlimitMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:loanlimit:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增借款额度")
    @PostMapping("/loanLimit/create")
    public Result<Long> loanlimitCreate(@RequestBody MdLoanLimit entity) {
        mdloanlimitMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:loanlimit:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新借款额度")
    @PostMapping("/loanLimit/update")
    public Result<Void> loanlimitUpdate(@RequestBody MdLoanLimit entity) {
        mdloanlimitMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:loanlimit:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除借款额度")
    @PostMapping("/loanLimit/{id}/delete")
    public Result<Void> loanlimitDelete(@PathVariable Long id) {
        mdloanlimitMapper.deleteById(id);
        return Result.success();
    }


    // 初始借款余额

    @RequiresPerm("md:loaninitial:list")
    @GetMapping("/loanInitial/page")
    public Result<PageResult<MdLoanInitial>> loaninitialPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdLoanInitial> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdLoanInitial::getRemark, keyword));
        }
        w.orderByDesc(MdLoanInitial::getId);
        Page<MdLoanInitial> p = mdloaninitialMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:loaninitial:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增初始借款余额")
    @PostMapping("/loanInitial/create")
    public Result<Long> loaninitialCreate(@RequestBody MdLoanInitial entity) {
        mdloaninitialMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:loaninitial:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新初始借款余额")
    @PostMapping("/loanInitial/update")
    public Result<Void> loaninitialUpdate(@RequestBody MdLoanInitial entity) {
        mdloaninitialMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:loaninitial:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除初始借款余额")
    @PostMapping("/loanInitial/{id}/delete")
    public Result<Void> loaninitialDelete(@PathVariable Long id) {
        mdloaninitialMapper.deleteById(id);
        return Result.success();
    }


    // 组织类型

    @RequiresPerm("md:orgtype:list")
    @GetMapping("/orgType/page")
    public Result<PageResult<MdOrgType>> orgtypePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdOrgType> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdOrgType::getRemark, keyword));
        }
        w.orderByDesc(MdOrgType::getId);
        Page<MdOrgType> p = mdorgtypeMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:orgtype:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增组织类型")
    @PostMapping("/orgType/create")
    public Result<Long> orgtypeCreate(@RequestBody MdOrgType entity) {
        mdorgtypeMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:orgtype:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新组织类型")
    @PostMapping("/orgType/update")
    public Result<Void> orgtypeUpdate(@RequestBody MdOrgType entity) {
        mdorgtypeMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:orgtype:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除组织类型")
    @PostMapping("/orgType/{id}/delete")
    public Result<Void> orgtypeDelete(@PathVariable Long id) {
        mdorgtypeMapper.deleteById(id);
        return Result.success();
    }


    // 现金流量码

    @RequiresPerm("md:cashflow:list")
    @GetMapping("/cashFlow/page")
    public Result<PageResult<MdCashFlow>> cashflowPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdCashFlow> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdCashFlow::getRemark, keyword));
        }
        w.orderByDesc(MdCashFlow::getId);
        Page<MdCashFlow> p = mdcashflowMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:cashflow:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增现金流量码")
    @PostMapping("/cashFlow/create")
    public Result<Long> cashflowCreate(@RequestBody MdCashFlow entity) {
        mdcashflowMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:cashflow:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新现金流量码")
    @PostMapping("/cashFlow/update")
    public Result<Void> cashflowUpdate(@RequestBody MdCashFlow entity) {
        mdcashflowMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:cashflow:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除现金流量码")
    @PostMapping("/cashFlow/{id}/delete")
    public Result<Void> cashflowDelete(@PathVariable Long id) {
        mdcashflowMapper.deleteById(id);
        return Result.success();
    }


    // 费用类型与现金流量码映射

    @RequiresPerm("md:itemcashmap:list")
    @GetMapping("/itemCashMap/page")
    public Result<PageResult<MdItemCashMap>> itemcashmapPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdItemCashMap> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdItemCashMap::getRemark, keyword));
        }
        w.orderByDesc(MdItemCashMap::getId);
        Page<MdItemCashMap> p = mditemcashmapMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:itemcashmap:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增费用类型与现金流量码映射")
    @PostMapping("/itemCashMap/create")
    public Result<Long> itemcashmapCreate(@RequestBody MdItemCashMap entity) {
        mditemcashmapMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:itemcashmap:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新费用类型与现金流量码映射")
    @PostMapping("/itemCashMap/update")
    public Result<Void> itemcashmapUpdate(@RequestBody MdItemCashMap entity) {
        mditemcashmapMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:itemcashmap:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除费用类型与现金流量码映射")
    @PostMapping("/itemCashMap/{id}/delete")
    public Result<Void> itemcashmapDelete(@PathVariable Long id) {
        mditemcashmapMapper.deleteById(id);
        return Result.success();
    }


    // 费用类型与预算科目映射

    /**
     * FINAL-01：费用类型→预算科目映射只读选项。登录租户用户即可读（不加 md:config）：
     * 单据发起页「选费用类型自动带出预算科目」依赖此数据；配置维护仍走下方需权限的 page/create/update。
     */
    @GetMapping("/itemBudgetMap/options")
    public Result<List<MdItemBudgetMap>> itembudgetmapOptions() {
        return Result.success(mditembudgetmapMapper.selectList(
                new LambdaQueryWrapper<MdItemBudgetMap>().orderByAsc(MdItemBudgetMap::getItemId)));
    }

    @RequiresPerm("md:itembudgetmap:list")
    @GetMapping("/itemBudgetMap/page")
    public Result<PageResult<MdItemBudgetMap>> itembudgetmapPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdItemBudgetMap> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdItemBudgetMap::getRemark, keyword));
        }
        w.orderByDesc(MdItemBudgetMap::getId);
        Page<MdItemBudgetMap> p = mditembudgetmapMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:itembudgetmap:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增费用类型与预算科目映射")
    @PostMapping("/itemBudgetMap/create")
    public Result<Long> itembudgetmapCreate(@RequestBody MdItemBudgetMap entity) {
        mditembudgetmapMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:itembudgetmap:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新费用类型与预算科目映射")
    @PostMapping("/itemBudgetMap/update")
    public Result<Void> itembudgetmapUpdate(@RequestBody MdItemBudgetMap entity) {
        mditembudgetmapMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:itembudgetmap:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除费用类型与预算科目映射")
    @PostMapping("/itemBudgetMap/{id}/delete")
    public Result<Void> itembudgetmapDelete(@PathVariable Long id) {
        mditembudgetmapMapper.deleteById(id);
        return Result.success();
    }


    // 成本中心预算组织映射

    @RequiresPerm("md:costcentermap:list")
    @GetMapping("/costCenterMap/page")
    public Result<PageResult<MdCostCenterMap>> costcentermapPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdCostCenterMap> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdCostCenterMap::getRemark, keyword));
        }
        w.orderByDesc(MdCostCenterMap::getId);
        Page<MdCostCenterMap> p = mdcostcentermapMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:costcentermap:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增成本中心预算组织映射")
    @PostMapping("/costCenterMap/create")
    public Result<Long> costcentermapCreate(@RequestBody MdCostCenterMap entity) {
        mdcostcentermapMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:costcentermap:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新成本中心预算组织映射")
    @PostMapping("/costCenterMap/update")
    public Result<Void> costcentermapUpdate(@RequestBody MdCostCenterMap entity) {
        mdcostcentermapMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:costcentermap:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除成本中心预算组织映射")
    @PostMapping("/costCenterMap/{id}/delete")
    public Result<Void> costcentermapDelete(@PathVariable Long id) {
        mdcostcentermapMapper.deleteById(id);
        return Result.success();
    }


    // 交通工具

    @RequiresPerm("md:vehicle:list")
    @GetMapping("/vehicle/page")
    public Result<PageResult<MdVehicle>> vehiclePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdVehicle> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdVehicle::getRemark, keyword));
        }
        w.orderByDesc(MdVehicle::getId);
        Page<MdVehicle> p = mdvehicleMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("md:vehicle:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增交通工具")
    @PostMapping("/vehicle/create")
    public Result<Long> vehicleCreate(@RequestBody MdVehicle entity) {
        mdvehicleMapper.insert(entity);
        return Result.success(entity.getId());
    }

    @RequiresPerm("md:vehicle:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新交通工具")
    @PostMapping("/vehicle/update")
    public Result<Void> vehicleUpdate(@RequestBody MdVehicle entity) {
        mdvehicleMapper.updateById(entity);
        return Result.success();
    }

    @RequiresPerm("md:vehicle:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除交通工具")
    @PostMapping("/vehicle/{id}/delete")
    public Result<Void> vehicleDelete(@PathVariable Long id) {
        mdvehicleMapper.deleteById(id);
        return Result.success();
    }


    /**
     * 存活集内的业务编码占用判定。
     * 租户条件由 MP 租户拦截器叠加，软删行由 @TableLogic 自动过滤，故此处无需显式加。
     * excludeId 为改码时排除自身行；空码放行（必填校验由前端/入库前另管）。
     */
    private <T extends TenantAuditBase> boolean codeTaken(BaseMapper<T> mapper, SFunction<T, ?> codeGetter,
                                                          Object code, Long excludeId) {
        if (code == null || (code instanceof String str && str.isBlank())) {
            return false;
        }
        List<T> hits = mapper.selectList(new LambdaQueryWrapper<T>().eq(codeGetter, code));
        for (T hit : hits) {
            if (excludeId == null || !Objects.equals(hit.getId(), excludeId)) {
                return true;
            }
        }
        return false;
    }

    private <T> Result<T> duplicateCode() {
        return Result.fail(ErrorCode.DATA_ALREADY_EXISTS.getCode(), ErrorCode.DATA_ALREADY_EXISTS.getMessage());
    }

    /**
     * 软删行仍占用业务编码位，会让「删除后用同码重建」撞唯一键，故退场时把编码改成不可复用的历史态。
     */
    private String historyCode(String code, Long id) {
        if (code == null || code.contains("#")) {
            return code;
        }
        return code + "#" + id;
    }

    /** 预算期间的键是复合的（年份＋期次），单列 helper 覆盖不了 */
    private boolean budgetPeriodTaken(MdBudgetPeriod e, Long excludeId) {
        if (e.getPeriodYear() == null || e.getPeriodNo() == null) {
            return false;
        }
        LambdaQueryWrapper<MdBudgetPeriod> w = new LambdaQueryWrapper<>();
        w.eq(MdBudgetPeriod::getPeriodYear, e.getPeriodYear()).eq(MdBudgetPeriod::getPeriodNo, e.getPeriodNo());
        if (excludeId != null) {
            w.ne(MdBudgetPeriod::getId, excludeId);
        }
        return mdbudgetperiodMapper.selectCount(w) > 0;
    }


    // 金融机构
    @RequiresPerm("md:fininst:list")
    @GetMapping("/bank/page")
    public Result<PageResult<MdBank>> bankPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdBank> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdBank::getBankName, keyword).or().like(MdBank::getBankCode, keyword));
        w.orderByDesc(MdBank::getId);
        Page<MdBank> p = mdbankMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:fininst:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增金融机构")
    @PostMapping("/bank/create")
    public Result<Long> bankCreate(@RequestBody MdBank e) {
        if (codeTaken(mdbankMapper, MdBank::getBankCode, e.getBankCode(), null)) return duplicateCode();
        mdbankMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:fininst:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新金融机构")
    @PostMapping("/bank/update")
    public Result<Void> bankUpdate(@RequestBody MdBank e) {
        if (codeTaken(mdbankMapper, MdBank::getBankCode, e.getBankCode(), e.getId())) return duplicateCode();
        mdbankMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:fininst:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除金融机构")
    @PostMapping("/bank/{id}/delete")
    public Result<Void> bankDelete(@PathVariable Long id) {
        MdBank exist = mdbankMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setBankCode(historyCode(exist.getBankCode(), exist.getId()));
        mdbankMapper.updateById(exist);
        mdbankMapper.deleteById(id);
        return Result.success();
    }

    // 员工等级
    @RequiresPerm("md:employeelevel:list")
    @GetMapping("/employeeLevel/page")
    public Result<PageResult<MdEmployeeLevel>> employeeLevelPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdEmployeeLevel> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdEmployeeLevel::getLevelName, keyword).or().like(MdEmployeeLevel::getLevelCode, keyword));
        w.orderByAsc(MdEmployeeLevel::getSort);
        Page<MdEmployeeLevel> p = mdemployeelevelMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:employeelevel:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增员工等级")
    @PostMapping("/employeeLevel/create")
    public Result<Long> employeeLevelCreate(@RequestBody MdEmployeeLevel e) {
        if (codeTaken(mdemployeelevelMapper, MdEmployeeLevel::getLevelCode, e.getLevelCode(), null)) return duplicateCode();
        mdemployeelevelMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:employeelevel:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新员工等级")
    @PostMapping("/employeeLevel/update")
    public Result<Void> employeeLevelUpdate(@RequestBody MdEmployeeLevel e) {
        if (codeTaken(mdemployeelevelMapper, MdEmployeeLevel::getLevelCode, e.getLevelCode(), e.getId())) return duplicateCode();
        mdemployeelevelMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:employeelevel:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除员工等级")
    @PostMapping("/employeeLevel/{id}/delete")
    public Result<Void> employeeLevelDelete(@PathVariable Long id) {
        MdEmployeeLevel exist = mdemployeelevelMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setLevelCode(historyCode(exist.getLevelCode(), exist.getId()));
        mdemployeelevelMapper.updateById(exist);
        mdemployeelevelMapper.deleteById(id);
        return Result.success();
    }

    // 记账配置
    @RequiresPerm("md:glconfig:list")
    @GetMapping("/glConfig/page")
    public Result<PageResult<MdGlConfig>> glConfigPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdGlConfig> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdGlConfig::getItemName, keyword));
        w.orderByDesc(MdGlConfig::getId);
        Page<MdGlConfig> p = mdglconfigMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:glconfig:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增记账配置")
    @PostMapping("/glConfig/create")
    public Result<Long> glConfigCreate(@RequestBody MdGlConfig e) { mdglconfigMapper.insert(e); return Result.success(e.getId()); }
    @RequiresPerm("md:glconfig:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新记账配置")
    @PostMapping("/glConfig/update")
    public Result<Void> glConfigUpdate(@RequestBody MdGlConfig e) { mdglconfigMapper.updateById(e); return Result.success(); }
    @RequiresPerm("md:glconfig:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除记账配置")
    @PostMapping("/glConfig/{id}/delete")
    public Result<Void> glConfigDelete(@PathVariable Long id) { mdglconfigMapper.deleteById(id); return Result.success(); }

    // 收支设置
    @RequiresPerm("md:incomeexpense:list")
    @GetMapping("/incomeExpense/page")
    public Result<PageResult<MdIncomeExpense>> incomeExpensePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdIncomeExpense> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdIncomeExpense::getIeName, keyword).or().like(MdIncomeExpense::getIeCode, keyword));
        w.orderByAsc(MdIncomeExpense::getIeCode);
        Page<MdIncomeExpense> p = mdincomeexpenseMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:incomeexpense:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增收支设置")
    @PostMapping("/incomeExpense/create")
    public Result<Long> incomeExpenseCreate(@RequestBody MdIncomeExpense e) {
        if (codeTaken(mdincomeexpenseMapper, MdIncomeExpense::getIeCode, e.getIeCode(), null)) return duplicateCode();
        mdincomeexpenseMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:incomeexpense:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新收支设置")
    @PostMapping("/incomeExpense/update")
    public Result<Void> incomeExpenseUpdate(@RequestBody MdIncomeExpense e) {
        if (codeTaken(mdincomeexpenseMapper, MdIncomeExpense::getIeCode, e.getIeCode(), e.getId())) return duplicateCode();
        mdincomeexpenseMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:incomeexpense:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除收支设置")
    @PostMapping("/incomeExpense/{id}/delete")
    public Result<Void> incomeExpenseDelete(@PathVariable Long id) {
        MdIncomeExpense exist = mdincomeexpenseMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setIeCode(historyCode(exist.getIeCode(), exist.getId()));
        mdincomeexpenseMapper.updateById(exist);
        mdincomeexpenseMapper.deleteById(id);
        return Result.success();
    }

    // 借款付款类型
    @RequiresPerm("md:loanpaytype:list")
    @GetMapping("/loanPayType/page")
    public Result<PageResult<MdLoanPayType>> loanPayTypePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdLoanPayType> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdLoanPayType::getTypeName, keyword).or().like(MdLoanPayType::getTypeCode, keyword));
        w.orderByAsc(MdLoanPayType::getTypeCode);
        Page<MdLoanPayType> p = mdloanpaytypeMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:loanpaytype:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增借款付款类型")
    @PostMapping("/loanPayType/create")
    public Result<Long> loanPayTypeCreate(@RequestBody MdLoanPayType e) {
        if (codeTaken(mdloanpaytypeMapper, MdLoanPayType::getTypeCode, e.getTypeCode(), null)) return duplicateCode();
        mdloanpaytypeMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:loanpaytype:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新借款付款类型")
    @PostMapping("/loanPayType/update")
    public Result<Void> loanPayTypeUpdate(@RequestBody MdLoanPayType e) {
        if (codeTaken(mdloanpaytypeMapper, MdLoanPayType::getTypeCode, e.getTypeCode(), e.getId())) return duplicateCode();
        mdloanpaytypeMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:loanpaytype:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除借款付款类型")
    @PostMapping("/loanPayType/{id}/delete")
    public Result<Void> loanPayTypeDelete(@PathVariable Long id) {
        MdLoanPayType exist = mdloanpaytypeMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setTypeCode(historyCode(exist.getTypeCode(), exist.getId()));
        mdloanpaytypeMapper.updateById(exist);
        mdloanpaytypeMapper.deleteById(id);
        return Result.success();
    }


    // 预算类别
    @RequiresPerm("md:budgetcategory:list")
    @GetMapping("/budgetCategory/page")
    public Result<PageResult<MdBudgetCategory>> budgetCategoryPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdBudgetCategory> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdBudgetCategory::getCategoryName, keyword).or().like(MdBudgetCategory::getCategoryCode, keyword));
        w.orderByAsc(MdBudgetCategory::getSort);
        Page<MdBudgetCategory> p = mdbudgetcategoryMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:budgetcategory:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增预算类别")
    @PostMapping("/budgetCategory/create")
    public Result<Long> budgetCategoryCreate(@RequestBody MdBudgetCategory e) {
        if (codeTaken(mdbudgetcategoryMapper, MdBudgetCategory::getCategoryCode, e.getCategoryCode(), null)) return duplicateCode();
        mdbudgetcategoryMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:budgetcategory:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新预算类别")
    @PostMapping("/budgetCategory/update")
    public Result<Void> budgetCategoryUpdate(@RequestBody MdBudgetCategory e) {
        if (codeTaken(mdbudgetcategoryMapper, MdBudgetCategory::getCategoryCode, e.getCategoryCode(), e.getId())) return duplicateCode();
        mdbudgetcategoryMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:budgetcategory:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除预算类别")
    @PostMapping("/budgetCategory/{id}/delete")
    public Result<Void> budgetCategoryDelete(@PathVariable Long id) {
        MdBudgetCategory exist = mdbudgetcategoryMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setCategoryCode(historyCode(exist.getCategoryCode(), exist.getId()));
        mdbudgetcategoryMapper.updateById(exist);
        mdbudgetcategoryMapper.deleteById(id);
        return Result.success();
    }

    // 预算组织
    @RequiresPerm("md:budgetorg:list")
    @GetMapping("/budgetOrg/page")
    public Result<PageResult<MdBudgetOrg>> budgetOrgPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdBudgetOrg> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdBudgetOrg::getOrgName, keyword).or().like(MdBudgetOrg::getOrgCode, keyword));
        w.orderByAsc(MdBudgetOrg::getOrgCode);
        Page<MdBudgetOrg> p = mdbudgetorgMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:budgetorg:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增预算组织")
    @PostMapping("/budgetOrg/create")
    public Result<Long> budgetOrgCreate(@RequestBody MdBudgetOrg e) {
        if (codeTaken(mdbudgetorgMapper, MdBudgetOrg::getOrgCode, e.getOrgCode(), null)) return duplicateCode();
        mdbudgetorgMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:budgetorg:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新预算组织")
    @PostMapping("/budgetOrg/update")
    public Result<Void> budgetOrgUpdate(@RequestBody MdBudgetOrg e) {
        if (codeTaken(mdbudgetorgMapper, MdBudgetOrg::getOrgCode, e.getOrgCode(), e.getId())) return duplicateCode();
        mdbudgetorgMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:budgetorg:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除预算组织")
    @PostMapping("/budgetOrg/{id}/delete")
    public Result<Void> budgetOrgDelete(@PathVariable Long id) {
        MdBudgetOrg exist = mdbudgetorgMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setOrgCode(historyCode(exist.getOrgCode(), exist.getId()));
        mdbudgetorgMapper.updateById(exist);
        mdbudgetorgMapper.deleteById(id);
        return Result.success();
    }

    // 预算期间
    @RequiresPerm("md:budgetperiod:list")
    @GetMapping("/budgetPeriod/page")
    public Result<PageResult<MdBudgetPeriod>> budgetPeriodPage(PageQuery q, Integer periodYear) {
        LambdaQueryWrapper<MdBudgetPeriod> w = new LambdaQueryWrapper<>();
        if (periodYear != null) w.eq(MdBudgetPeriod::getPeriodYear, periodYear);
        w.orderByAsc(MdBudgetPeriod::getPeriodYear).orderByAsc(MdBudgetPeriod::getPeriodNo);
        Page<MdBudgetPeriod> p = mdbudgetperiodMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:budgetperiod:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增预算期间")
    @PostMapping("/budgetPeriod/create")
    public Result<Long> budgetPeriodCreate(@RequestBody MdBudgetPeriod e) {
        if (budgetPeriodTaken(e, null)) return duplicateCode();
        mdbudgetperiodMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:budgetperiod:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新预算期间")
    @PostMapping("/budgetPeriod/update")
    public Result<Void> budgetPeriodUpdate(@RequestBody MdBudgetPeriod e) {
        if (budgetPeriodTaken(e, e.getId())) return duplicateCode();
        mdbudgetperiodMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:budgetperiod:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除预算期间")
    @PostMapping("/budgetPeriod/{id}/delete")
    public Result<Void> budgetPeriodDelete(@PathVariable Long id) {
        MdBudgetPeriod exist = mdbudgetperiodMapper.selectById(id);
        if (exist == null) return Result.success();
        if (exist.getPeriodNo() == null || exist.getPeriodNo() >= 0) {
            // 期次没有可拼 '#' 的字符位，取负既出月份域又逐行唯一，等价于释放复合键位
            exist.setPeriodNo(-exist.getId().intValue());
            mdbudgetperiodMapper.updateById(exist);
        }
        mdbudgetperiodMapper.deleteById(id);
        return Result.success();
    }


    // 合同类型
    @RequiresPerm("md:contracttype:list")
    @GetMapping("/contractType/page")
    public Result<PageResult<MdContractType>> contractTypePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdContractType> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(MdContractType::getTypeName, keyword).or().like(MdContractType::getTypeCode, keyword));
        w.orderByAsc(MdContractType::getTypeCode);
        Page<MdContractType> p = mdcontracttypeMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:contracttype:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增合同类型")
    @PostMapping("/contractType/create")
    public Result<Long> contractTypeCreate(@RequestBody MdContractType e) {
        if (codeTaken(mdcontracttypeMapper, MdContractType::getTypeCode, e.getTypeCode(), null)) return duplicateCode();
        mdcontracttypeMapper.insert(e);
        return Result.success(e.getId());
    }
    @RequiresPerm("md:contracttype:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新合同类型")
    @PostMapping("/contractType/update")
    public Result<Void> contractTypeUpdate(@RequestBody MdContractType e) {
        if (codeTaken(mdcontracttypeMapper, MdContractType::getTypeCode, e.getTypeCode(), e.getId())) return duplicateCode();
        mdcontracttypeMapper.updateById(e);
        return Result.success();
    }
    @RequiresPerm("md:contracttype:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除合同类型")
    @PostMapping("/contractType/{id}/delete")
    public Result<Void> contractTypeDelete(@PathVariable Long id) {
        MdContractType exist = mdcontracttypeMapper.selectById(id);
        if (exist == null) return Result.success();
        exist.setTypeCode(historyCode(exist.getTypeCode(), exist.getId()));
        mdcontracttypeMapper.updateById(exist);
        mdcontracttypeMapper.deleteById(id);
        return Result.success();
    }


    // 在建工程（需求补齐：基础数据与配置 > 在建工程）
    @RequiresPerm("md:cip:list")
    @GetMapping("/cip/page")
    public Result<PageResult<MdCip>> cipPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdCip> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdCip::getCipCode, keyword).or().like(MdCip::getCipName, keyword).or().like(MdCip::getCompanyName, keyword));
        }
        w.orderByAsc(MdCip::getCipCode);
        Page<MdCip> p = mdcipMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:cip:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增在建工程")
    @PostMapping("/cip/create")
    public Result<Long> cipCreate(@RequestBody MdCip e) { return Result.success(mdcipService.create(e)); }
    @RequiresPerm("md:cip:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新在建工程")
    @PostMapping("/cip/update")
    public Result<Void> cipUpdate(@RequestBody MdCip e) { mdcipService.update(e); return Result.success(); }
    @RequiresPerm("md:cip:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除在建工程")
    @PostMapping("/cip/{id}/delete")
    public Result<Void> cipDelete(@PathVariable Long id) { mdcipMapper.deleteById(id); return Result.success(); }

    // 在建工程：成本归集
    @RequiresPerm("md:cip:update")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增在建工程成本归集明细")
    @PostMapping("/cip/{id}/cost/add")
    public Result<Long> cipCostAdd(@PathVariable Long id, @RequestBody MdCipCost cost) {
        return Result.success(mdcipService.addCost(id, cost));
    }
    @RequiresPerm("md:cip:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除在建工程成本归集明细")
    @PostMapping("/cip/cost/{costId}/delete")
    public Result<Void> cipCostDelete(@PathVariable Long costId) { mdcipService.deleteCost(costId); return Result.success(); }
    @RequiresPerm("md:cip:list")
    @GetMapping("/cip/{id}/cost/page")
    public Result<PageResult<MdCipCost>> cipCostPage(@PathVariable Long id, PageQuery q) {
        return Result.success(mdcipService.costPage(id, q));
    }

    // 在建工程：转固定资产
    @RequiresPerm("md:cip:transfer")
    @OperLog(module = "MD_CONFIG", operType = "TRANSFER", desc = "在建工程转固定资产")
    @PostMapping("/cip/{id}/transferToAsset")
    public Result<Long> cipTransferToAsset(@PathVariable Long id, @RequestBody MdCipTransferRequest req) {
        return Result.success(mdcipService.transferToAsset(id, req));
    }

    // 预算类型（需求补齐：预算管理 > 预算类型）
    @RequiresPerm("md:budgettype:list")
    @GetMapping("/budgetType/page")
    public Result<PageResult<MdBudgetType>> budgetTypePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<MdBudgetType> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(MdBudgetType::getTypeName, keyword).or().like(MdBudgetType::getRemark, keyword));
        }
        w.orderByAsc(MdBudgetType::getSort);
        Page<MdBudgetType> p = mdbudgettypeMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }
    @RequiresPerm("md:budgettype:create")
    @OperLog(module = "MD_CONFIG", operType = "INSERT", desc = "新增预算类型")
    @PostMapping("/budgetType/create")
    public Result<Long> budgetTypeCreate(@RequestBody MdBudgetType e) { mdbudgettypeMapper.insert(e); return Result.success(e.getId()); }
    @RequiresPerm("md:budgettype:update")
    @OperLog(module = "MD_CONFIG", operType = "UPDATE", desc = "更新预算类型")
    @PostMapping("/budgetType/update")
    public Result<Void> budgetTypeUpdate(@RequestBody MdBudgetType e) { mdbudgettypeMapper.updateById(e); return Result.success(); }
    @RequiresPerm("md:budgettype:delete")
    @OperLog(module = "MD_CONFIG", operType = "DELETE", desc = "删除预算类型")
    @PostMapping("/budgetType/{id}/delete")
    public Result<Void> budgetTypeDelete(@PathVariable Long id) { mdbudgettypeMapper.deleteById(id); return Result.success(); }

}
