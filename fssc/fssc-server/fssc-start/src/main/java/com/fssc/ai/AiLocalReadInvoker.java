package com.fssc.ai;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 目录动作的本地只读执行器（巡检 job 用）。
 *
 * <p>为什么不发假 token 走 HTTP：TokenService 把 perms 烘进 JWT，但下游
 * {@code PermissionAspect}/{@code DataScopeHolder} 以"会话重载的权限"为准 ——
 * 伪 token 会造成"网关放行、业务 403"的双标。巡检 job 的正确姿势与既有
 * {@code WfTimeoutJob} 完全一致：调度线程 {@code TenantContext.callAs} 建立租户语义后
 * 直接调同一 Service 入口（同一条业务代码，不存在第二套规则）。
 *
 * <p>安全边界：只允许目录判定为 read∧aiAllowed∧LOW 的动作。控制器是切面代理，反射调用同样会触发
 * {@code PermissionAspect}（调用方必须先把 owner 绑进 {@code UserContext}，见 AiAskService）；
 * 注解形态在此再复核一次作双保险。任何参数形态不确定的方法一律拒跑（宁漏不错）。
 */
@Slf4j
@Component
public class AiLocalReadInvoker {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private static final ParameterNameDiscoverer PND = new DefaultParameterNameDiscoverer();

    public AiLocalReadInvoker(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                              ObjectMapper objectMapper,
                              ApplicationContext applicationContext) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    /** HandlerMethod 可能以 bean 名（String）惰性持有目标，反射调用前必须解析成实例。 */
    private Object resolveBean(HandlerMethod hm) {
        Object b = hm.getBean();
        return b instanceof String name ? applicationContext.getBean(name) : b;
    }

    public Object invoke(AiAction action, Map<String, Object> args) {
        HandlerMethod hm = findHandler(action);
        Method method = hm.getMethod();

        RequiresPerm rp = method.getAnnotation(RequiresPerm.class);
        if (rp != null && (rp.value() == null || rp.value().length == 0)) {
            throw new BizException(ErrorCode.FORBIDDEN, "巡检动作权限注解形态异常，拒绝本地执行：" + action.getCode());
        }
        RequiresFeature rf = method.getAnnotation(RequiresFeature.class);
        if (rf != null) {
            log.info("本地巡检执行：动作 {} 带套餐开关 {}，job 线程无套餐上下文，按启用处理（如已停购请在规则里停用本条）",
                    action.getCode(), rf.value());
        }

        String[] names = PND.getParameterNames(method);
        Parameter[] ps = method.getParameters();
        Object[] argv = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            Class<?> t = ps[i].getType();
            String n = names == null ? null : names[i];
            if (HttpServletRequest.class.isAssignableFrom(t) || HttpServletResponse.class.isAssignableFrom(t)) {
                throw new BizException(ErrorCode.FORBIDDEN, "动作依赖请求对象，无法本地巡检执行：" + action.getCode());
            }
            PathVariable pv = ps[i].getAnnotation(PathVariable.class);
            RequestBody rb = ps[i].getAnnotation(RequestBody.class);
            if (pv != null) {
                throw new BizException(ErrorCode.FORBIDDEN, "动作含路径参数，巡检参数模板暂不支持：" + action.getCode());
            } else if (rb != null || isSimple(t)) {
                Object v = args.get(n);
                if (v == null && rb != null) {
                    v = objectMapper.convertValue(args, t);
                }
                argv[i] = v == null ? null : objectMapper.convertValue(v, t);
            } else {
                RequestParam rpm = ps[i].getAnnotation(RequestParam.class);
                if (rpm != null && rpm.required()) {
                    throw new BizException(ErrorCode.FORBIDDEN, "动作有必填查询参数 " + n + "，请在参数模板中给出：" + action.getCode());
                }
                if (isPageLike(t)) {
                    long pn = toLong(args.get("pageNum"), 1L);
                    long psz = toLong(args.get("pageSize"), 500L);
                    argv[i] = new Page<>(pn, psz);
                } else if (t.getName().startsWith("com.fssc.")) {
                    // 查询对象（XxxQuery POJO）：整个参数模板 map 绑进去
                    argv[i] = objectMapper.convertValue(args, t);
                } else {
                    argv[i] = defaultOf(t);
                }
            }
        }
        try {
            Object ret = method.invoke(resolveBean(hm), argv);
            if (ret instanceof Result<?> r) {
                if (r.getCode() != 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "巡检动作返回业务错误：" + r.getMsg());
                }
                return r.getData();
            }
            return ret;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException ite && ite.getTargetException() != null
                    ? ite.getTargetException() : ex;
            if (cause instanceof BizException be) {
                throw be;
            }
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "巡检动作执行失败：" + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private HandlerMethod findHandler(AiAction action) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> en
                : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = en.getValue();
            if (!hm.getMethod().getName().equals(action.getJavaMethod())) {
                continue;
            }
            var info = en.getKey();
            // Boot3/6 默认 PathPatternParser：经典 PatternsCondition 常为 null，两轨都要比对
            boolean urlHit =
                    (info.getPatternsCondition() != null
                            && info.getPatternsCondition().getPatterns().contains(action.getUrl()))
                    || (info.getPathPatternsCondition() != null
                            && info.getPathPatternsCondition().getPatterns().stream()
                                    .anyMatch(p -> action.getUrl().equals(p.getPatternString())));
            if (urlHit
                    && (info.getMethodsCondition().getMethods().isEmpty()
                        || info.getMethodsCondition().getMethods().stream()
                            .anyMatch(h -> h.name().equalsIgnoreCase(action.getMethod())))) {
                return hm;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "未找到动作对应的处理器（目录与代码可能已漂移，请重跑目录生成器）："
                + action.getCode() + " " + action.getMethod() + " " + action.getUrl());
    }

    private static long toLong(Object v, long dft) {
        if (v == null) {
            return dft;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    private boolean isSimple(Class<?> t) {
        return t == String.class || Number.class.isAssignableFrom(t) || t == boolean.class || t == Boolean.class
                || t == LocalDate.class || t == LocalDateTime.class || t == Date.class;
    }

    /** MP 的 Page/IPage 形态参数：不能由 Map 直接 convert，给默认分页。 */
    private boolean isPageLike(Class<?> t) {
        return Page.class.isAssignableFrom(t)
                || IPage.class.isAssignableFrom(t);
    }

    private Object defaultOf(Class<?> t) {
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == boolean.class) return false;
        if (t == double.class) return 0d;
        return null;
    }
}
