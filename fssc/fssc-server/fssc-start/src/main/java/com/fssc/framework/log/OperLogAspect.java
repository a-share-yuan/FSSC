package com.fssc.framework.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.platform.entity.PfOperLog;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysOperLog;
import com.fssc.system.mapper.SysDeptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作审计切面（双轨）。
 * <p>
 * 记录：操作人/模块/类型/描述/方法/URI/参数(脱敏)/结果/耗时/成败。
 * 审计表（pf_oper_log / sys_oper_log）均无 del_flag，Mapper 只提供 insert —— 不可删除。
 * <p>
 * 本切面在请求线程内同步采集上下文并组装实体，仅把 INSERT 动作交给 {@link OperLogWriter}
 * 异步落库（专用线程池），避免高并发下写库阻塞业务线程；日志失败仍不影响业务结果。
 * <p>
 * 【D-DEC-178】{@code sys_oper_log.oper_dept_name} 此前"列在、实体字段在、全仓零赋值"
 * ⇒ 现网 604/604 行全 NULL，日志页/审计面的"办理人部门"无从追溯。
 * {@link LoginUser} 只带 deptId（部门名不入 Token，免得改名后长期签发旧名），
 * 故在<b>请求线程内</b>按 deptId 反查 sys_dept（该表受租户过滤，异步写入线程没有租户上下文，
 * 交给 {@link OperLogWriter} 查会直接 fail-closed 抛 TENANT_CONTEXT_MISSING），
 * 再用短 TTL 缓存收敛重复主键查询。查不到/查询异常一律留 NULL——审计列宁缺不造。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperLogWriter operLogWriter;
    private final ObjectMapper objectMapper;
    /** D-DEC-178：按 deptId 反查部门名（sys_dept 主键查询，租户条件由拦截器注入） */
    private final SysDeptMapper sysDeptMapper;

    /** 部门名缓存 TTL（毫秒）＝改名后最长可见滞后 */
    private static final long DEPT_NAME_TTL_MS = 600_000L;
    /** 部门名缓存条数上限（部门数量级远小于此；溢出整体清空，防无界增长） */
    private static final int DEPT_NAME_MAX = 512;
    /** deptId → 部门名取样；空串为"查无此部门"的负缓存，免每笔白查一次 */
    private static final Map<Long, DeptNameSample> DEPT_NAME_CACHE = new ConcurrentHashMap<>();

    private record DeptNameSample(String name, long sampledAt) {
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                save(pjp, operLog, result, error, System.currentTimeMillis() - start);
            } catch (Exception ex) {
                // 审计失败不影响业务结果，但要留痕
                log.error("审计日志写入失败", ex);
            }
        }
    }

    private void save(ProceedingJoinPoint pjp, OperLog operLog, Object result, Throwable error, long cost) {
        LoginUser u = UserContext.get();
        HttpServletRequest req = currentRequest();
        boolean ok = error == null;

        if (u != null && u.isPlatform()) {
            PfOperLog l = new PfOperLog();
            l.setModule(operLog.module());
            l.setOperType(operLog.operType());
            l.setOperDesc(operLog.desc());
            l.setMethod(truncate(((MethodSignature) pjp.getSignature()).getMethod().toString(), 500)); // SOX-04: 全签名超长致 sys_oper_log 写入失败
            l.setRequestMethod(req == null ? null : req.getMethod());
            l.setRequestUri(req == null ? null : req.getRequestURI());
            l.setRequestParam(truncate(toJson(pjp.getArgs())));
            l.setResponseData(truncate(toJson(result)));
            l.setOperUserId(u.getUserId());
            l.setOperUsername(u.getUsername());
            l.setOperIp(req == null ? null : req.getRemoteAddr());
            l.setStatus(ok ? 1 : 0);
            l.setErrorMsg(ok ? null : readableError(error));
            l.setCostMs(cost);
            l.setOperTime(LocalDateTime.now());
            operLogWriter.writePf(l);
        } else if (u != null && u.isTenant() && TenantContext.hasContext() && !TenantContext.isIgnoreTenant()) {
            SysOperLog l = new SysOperLog();
            l.setTenantId(TenantContext.getTenantId());
            l.setTraceId(org.slf4j.MDC.get(com.fssc.framework.trace.TraceIdFilter.MDC_KEY));
            l.setTitle(operLog.module());
            l.setBusinessType(operLog.operType());
            l.setMethod(truncate(((MethodSignature) pjp.getSignature()).getMethod().toString(), 500)); // SOX-04: 全签名超长致 sys_oper_log 写入失败
            l.setRequestMethod(req == null ? null : req.getMethod());
            l.setOperUrl(req == null ? null : req.getRequestURI());
            l.setOperParam(truncate(toJson(pjp.getArgs())));
            l.setJsonResult(truncate(toJson(result)));
            l.setOperUserId(u.getUserId());
            l.setOperUserName(u.getUsername());
            l.setOperDeptName(resolveDeptName(u.getDeptId())); // D-DEC-178：办理人部门，留空只在确无部门时出现
            l.setOperIp(req == null ? null : req.getRemoteAddr());
            l.setStatus(ok ? 0 : 1);
            l.setErrorMsg(ok ? null : readableError(error));
            l.setCostTime(cost);
            l.setOperTime(LocalDateTime.now());
            operLogWriter.writeSys(l);
        }
    }

    // ---------- 工具 ----------

    /**
     * R5-D-111：审计留痕的 error_msg 只落「异常类型＋业务可读摘要」，长度压到 200 以内。
     *
     * <p>改前实测：一次开票失败把 MyBatis 包装件的整段文本（含 {@code ### SQL:} 原文与
     * {@code for key '表名.PRIMARY'}）落进了 sys_oper_log.error_msg，日志页把库面结构整片摊给
     * 看日志的人；且 2000 字符上限让一条记录吃掉大半页。完整堆栈本来就由全局异常处理器
     * log.error 落到日志文件，页面用 trace_id 关联即可，留痕列不需要复刻堆栈。</p>
     *
     * <p>口径：业务异常（BizException）的文案本就是给用户的，原样留（截 200）；
     * 技术异常只留最内层异常的类型名＋一句业务含义，SQL 原文/索引名/表名/mapper 路径一概不落。</p>
     */
    private String readableError(Throwable error) {
        if (error instanceof com.fssc.common.exception.BizException) {
            String m = error.getMessage();
            return truncate(m == null || m.isBlank() ? "业务校验未通过" : m, 200);
        }
        Throwable root = error;
        if (error instanceof org.springframework.core.NestedRuntimeException nre) {
            root = nre.getMostSpecificCause();
        }
        String type = root.getClass().getSimpleName();
        String hint = TECH_ERROR_HINT.getOrDefault(type, "服务端处理失败");
        return truncate("技术异常 " + type + "：" + hint + "（详见日志，trace_id 关联）", 200);
    }

    /** 技术异常类型 → 一句业务可读含义；未列出的类型统一回"服务端处理失败"，不带出任何库面文本 */
    private static final Map<String, String> TECH_ERROR_HINT = Map.of(
            "SQLIntegrityConstraintViolationException", "数据已存在或与已有记录冲突",
            "DuplicateKeyException", "数据已存在或与已有记录冲突",
            "DeadlockLoserDataAccessException", "并发冲突，请重试",
            "CannotAcquireLockException", "并发冲突，请重试",
            "DataAccessResourceFailureException", "数据源暂不可用");

    /**
     * 按 deptId 取部门名（D-DEC-178）。
     *
     * <p><b>调用位置约束</b>：只能在请求线程（本切面组装实体处）调用——sys_dept 是租户过滤表，
     * 需要 {@link TenantContext}；异步写入线程里没有它，查了会抛 TENANT_CONTEXT_MISSING。</p>
     *
     * <p>失败语义：deptId 为空 / 部门查无 / 查询异常 ⇒ 返回 null（该列留空）。
     * 本方法<b>不向上抛</b>：部门是审计增强项，不能因为它让整条操作日志写不进库。</p>
     *
     * @return 部门名，或 null
     */
    private String resolveDeptName(Long deptId) {
        if (deptId == null || deptId <= 0L) {
            return null;
        }
        long now = System.currentTimeMillis();
        DeptNameSample cached = DEPT_NAME_CACHE.get(deptId);
        if (cached != null && now - cached.sampledAt() < DEPT_NAME_TTL_MS) {
            return cached.name().isEmpty() ? null : cached.name();
        }
        try {
            SysDept dept = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                    .select(SysDept::getDeptName)
                    .eq(SysDept::getId, deptId)
                    .last("limit 1"));
            String name = (dept == null || dept.getDeptName() == null) ? "" : dept.getDeptName().trim();
            if (DEPT_NAME_CACHE.size() >= DEPT_NAME_MAX) {
                DEPT_NAME_CACHE.clear();
            }
            DEPT_NAME_CACHE.put(deptId, new DeptNameSample(name, now));
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            // 不写缓存：下一笔重试；部门缺失不影响整行日志落库
            log.warn("操作日志部门反查失败 deptId={}: {}", deptId, e.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** 参数序列化 + 敏感字段脱敏（凭据类／令牌类／验证码类／外部接口密钥类） */
    private String toJson(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            return json.replaceAll("(\"(?:password|oldPassword|newPassword|pwd|token|secret|apiSecret|apiKey|verifyCode|captchaCode)\"\\s*:\\s*\")[^\"]*(\")", "$1******$2");
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String s) {
        return truncate(s, 2000);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
