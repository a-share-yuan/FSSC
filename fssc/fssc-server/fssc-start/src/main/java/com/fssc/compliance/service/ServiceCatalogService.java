package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ServiceCatalog;
import com.fssc.compliance.entity.SatisfactionSurvey;
import com.fssc.compliance.mapper.ServiceCatalogMapper;
import com.fssc.compliance.mapper.SatisfactionSurveyMapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.sharing.entity.OpAuditTask;
import com.fssc.sharing.mapper.OpAuditTaskMapper;
import java.math.RoundingMode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 服务目录与 SLA 管理：
 * - 服务目录 CRUD（共享中心对外服务项 + SLA 时效目标）
 * - SLA 监控：基于共享作业任务 op_audit_task 的 sla_time 计算超时预警与按时率
 * - 满意度调查：用户对共享服务的评分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceCatalogMapper catalogMapper;
    private final SatisfactionSurveyMapper satisfactionMapper;
    private final OpAuditTaskMapper auditTaskMapper;

    // 服务目录 CRUD

    public PageResult<ServiceCatalog> pageCatalog(long pageNum, long pageSize, String category, Integer status) {
        LambdaQueryWrapper<ServiceCatalog> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) qw.eq(ServiceCatalog::getCategory, category);
        if (status != null) qw.eq(ServiceCatalog::getStatus, status);
        qw.orderByAsc(ServiceCatalog::getId);
        Page<ServiceCatalog> p = catalogMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public ServiceCatalog getCatalog(Long id) {
        ServiceCatalog c = catalogMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.PARAM_INVALID, "服务目录不存在: " + id);
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createCatalog(ServiceCatalog c) {
        assertServiceCodeFree(c.getServiceCode(), null);
        if (c.getStatus() == null) c.setStatus(1);
        c.setTenantId(TenantContext.getTenantId());
        c.setCreateTime(LocalDateTime.now());
        catalogMapper.insert(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCatalog(ServiceCatalog c) {
        if (catalogMapper.selectById(c.getId()) == null)
            throw new BizException(ErrorCode.PARAM_INVALID, "服务目录不存在: " + c.getId());
        assertServiceCodeFree(c.getServiceCode(), c.getId());
        c.setUpdateTime(LocalDateTime.now());
        catalogMapper.updateById(c);
    }

    /** 服务编码在本租户内存活集唯一（租户条件由 MP 拦截器叠加，软删行由 @TableLogic 自动过滤） */
    private void assertServiceCodeFree(String code, Long selfId) {
        if (code == null || code.isBlank()) {
            return;
        }
        Long n = catalogMapper.selectCount(new LambdaQueryWrapper<ServiceCatalog>()
                .eq(ServiceCatalog::getServiceCode, code)
                .ne(selfId != null, ServiceCatalog::getId, selfId));
        if (n != null && n > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS, "服务编码已存在: " + code);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCatalog(Long id) {
        ServiceCatalog exist = catalogMapper.selectById(id);
        if (exist == null)
            throw new BizException(ErrorCode.PARAM_INVALID, "服务目录不存在: " + id);
        // 软删行仍占用 service_code，会让「删除后重建同码服务」撞唯一键；退场时改写为历史态让码位可用
        String code = exist.getServiceCode();
        if (code != null && !code.contains("#")) {
            exist.setServiceCode(code + "#" + exist.getId());
            catalogMapper.updateById(exist);
        }
        catalogMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableCatalog(Long id, Integer status) {
        ServiceCatalog c = getCatalog(id);
        c.setStatus(status);
        c.setUpdateTime(LocalDateTime.now());
        catalogMapper.updateById(c);
    }

    // SLA 监控

    /** SLA 超时预警：仍在处理且已超过 sla_time 的作业任务。 */
    public List<Map<String, Object>> slaMonitor() {
        List<OpAuditTask> overdue = auditTaskMapper.selectList(new LambdaQueryWrapper<OpAuditTask>()
                .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED")
                .lt(OpAuditTask::getSlaTime, LocalDateTime.now())
                .orderByAsc(OpAuditTask::getSlaTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OpAuditTask t : overdue) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("billId", t.getBillId());
            m.put("billNo", t.getBillNo());
            m.put("billType", t.getBillType());
            m.put("taskType", t.getTaskType());
            m.put("title", t.getTitle());
            m.put("poolStatus", t.getPoolStatus());
            m.put("slaTime", t.getSlaTime());
            m.put("overdueHours", Duration.between(t.getSlaTime(), LocalDateTime.now()).toHours());
            result.add(m);
        }
        return result;
    }

    /** SLA 统计：总量、按时完成、超时、按时率。 */
    public Map<String, Object> slaStats() {
        long total = auditTaskMapper.selectCount(new LambdaQueryWrapper<>());
        long finished = auditTaskMapper.selectCount(new LambdaQueryWrapper<OpAuditTask>()
                .in(OpAuditTask::getPoolStatus, "FINISHED"));
        long onTime = auditTaskMapper.selectCount(new LambdaQueryWrapper<OpAuditTask>()
                .in(OpAuditTask::getPoolStatus, "FINISHED")
                .apply("finish_time >= sla_time"));
        long overdue = auditTaskMapper.selectCount(new LambdaQueryWrapper<OpAuditTask>()
                .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED")
                .lt(OpAuditTask::getSlaTime, LocalDateTime.now()));
        long late = finished - onTime;
        BigDecimal onTimeRate = finished > 0
                ? BigDecimal.valueOf(onTime * 100.0 / finished).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("finished", finished);
        r.put("onTime", onTime);
        r.put("late", late);
        r.put("overdue", overdue);
        r.put("onTimeRate", onTimeRate);
        return r;
    }

    // 满意度调查

    @Transactional(rollbackFor = Exception.class)
    public void submitSatisfaction(SatisfactionSurvey s) {
        if (s.getScore() == null || s.getScore() < 1 || s.getScore() > 5)
            throw new BizException(ErrorCode.PARAM_INVALID, "满意度评分需在 1-5 之间");
        s.setTenantId(TenantContext.getTenantId());
        s.setCreateTime(LocalDateTime.now());
        satisfactionMapper.insert(s);
    }

    public PageResult<SatisfactionSurvey> pageSatisfaction(long pageNum, long pageSize, String serviceCode) {
        LambdaQueryWrapper<SatisfactionSurvey> qw = new LambdaQueryWrapper<>();
        if (serviceCode != null && !serviceCode.isBlank()) qw.eq(SatisfactionSurvey::getServiceCode, serviceCode);
        qw.orderByDesc(SatisfactionSurvey::getId);
        Page<SatisfactionSurvey> p = satisfactionMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 满意度统计：平均分、各分档数量。 */
    public Map<String, Object> satisfactionStats() {
        List<SatisfactionSurvey> all = satisfactionMapper.selectList(new LambdaQueryWrapper<>());
        long total = all.size();
        double avg = total == 0 ? 0 : all.stream().mapToInt(SatisfactionSurvey::getScore).average().orElse(0);
        long five = all.stream().filter(s -> s.getScore() == 5).count();
        long four = all.stream().filter(s -> s.getScore() == 4).count();
        long three = all.stream().filter(s -> s.getScore() == 3).count();
        long two = all.stream().filter(s -> s.getScore() == 2).count();
        long one = all.stream().filter(s -> s.getScore() == 1).count();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("avgScore", BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        r.put("score5", five);
        r.put("score4", four);
        r.put("score3", three);
        r.put("score2", two);
        r.put("score1", one);
        return r;
    }
}
