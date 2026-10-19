package com.fssc.framework.exception;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器（Web 适配层，放在 start 模块而非 common —— 保持 common 不依赖 Web）。
 * 所有 Controller 抛出的异常在此收敛为统一的 Result，前端只看 code/msg。
 * 平台日志与租户日志在此分流（P2 接入双轨审计）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 单文件上传上限（字节），与 fssc.file.max-size 同源，用于超限提示文案 */
    @Value("${fssc.file.max-size:20971520}")
    private long maxFileSize;

    /** 业务异常：直接取 code/msg */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex, HttpServletRequest req) {
        log.warn("业务异常 uri={} msg={}", req.getRequestURI(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 请求参数缺失异常：返回友好的参数错误提示 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        log.warn("参数缺失 uri={} param={}", req.getRequestURI(), ex.getParameterName());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "缺少必填参数: " + ex.getParameterName());
    }

    /**
     * 上传体积超限：Spring multipart 在进入 Controller 之前就抛，业务代码无法感知，
     * 若落到兜底分支前端只会看到"系统异常"（的 15MB 附件"静默拒绝"）。
     * 这里显式返回体积上限提示，让用户知道压缩后重试。
     */
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public Result<Void> handleUploadSize(Exception ex, HttpServletRequest req) {
        log.warn("上传体积超限 uri={} msg={}", req.getRequestURI(), ex.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                "附件体积超过上限（单文件 " + (maxFileSize / 1024 / 1024) + "MB），请压缩或拆分后重试");
    }

    /**
     * @Valid / @Validated 入参校验失败：汇总所有字段错误（去重）一次性返回，
     * 让用户一次看全"要改哪几项"，而不是逐个踩雷（第0批校验基座·字段级错误聚合）。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception ex) {
        List<FieldError> errors;
        if (ex instanceof MethodArgumentNotValidException m) {
            errors = m.getBindingResult().getFieldErrors();
        } else {
            errors = ((BindException) ex).getBindingResult().getFieldErrors();
        }
        String msg = errors.isEmpty()
                ? "参数校验失败"
                : errors.stream().map(FieldError::getDefaultMessage)
                        .filter(java.util.Objects::nonNull).distinct()
                        .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 路径/查询参数类型不匹配（V5 人工测试 OBS-01）：
     * 实测 {@code POST /api/tenant/pay/batch/{id}/confirm} 的 {@code {id}} 传批次号字符串
     * {@code PB202610190001}（应传数字主键）时，此前落到下方兜底分支回
     * {@code code=500「系统异常，请稍后重试」}，调用方无法区分"把单号当主键传了"与"服务端坏了"，
     * 会盲目重试同一笔。与本类既有 405 / 404 / 4090 分支同口径，此处显式回 400 并点出参数名与实参。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("参数类型不匹配 uri={} param={} value={} requiredType={}",
                req.getRequestURI(), ex.getName(), ex.getValue(),
                ex.getRequiredType() == null ? null : ex.getRequiredType().getSimpleName());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                "参数类型不匹配：" + ex.getName() + "=" + ex.getValue()
                        + "（应为 "
                        + (ex.getRequiredType() == null ? "正确类型" : ex.getRequiredType().getSimpleName())
                        + "）");
    }

    /**
     * 请求动词与端点不符（如对 @PostMapping 发 GET）。
     * 不显式接管就会落到下方兜底分支变成"系统异常"，调用方无法区分"动作选错了"和"服务端坏了"，
     * 会自动重试或误报故障——AI 网关按 A2 判据断言时尤其致命，故原样带出该端点支持的动词。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        log.warn("请求方法不支持 uri={} got={} supported={}",
                req.getRequestURI(), ex.getMethod(), ex.getSupportedHttpMethods());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "请求方法不支持: " + ex.getMethod() + "，该端点仅接受 " + ex.getSupportedHttpMethods()
                        + "（uri=" + req.getRequestURI() + "）");
    }

    /**
     * 路径本身不存在（D-N19）：Spring Boot 3 对未映射请求走静态资源兜底，抛
     * {@link NoResourceFoundException}；不显式接管就会落到下方兜底分支变成
     * {@code code=500「系统异常」}——调用方（尤其 AI 选型编出来的端点）无法区分
     * "路径我编错了"与"服务端坏了"，会误报故障或盲目重试。此处与 405 分支同口径显式回 404。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Result<Void> handleNotFound(Exception ex, HttpServletRequest req) {
        log.warn("端点不存在 uri={} msg={}", req.getRequestURI(), ex.getMessage());
        return Result.fail(ErrorCode.NOT_FOUND.getCode(),
                "端点不存在：" + req.getMethod() + " " + req.getRequestURI()
                        + "（本系统未登记该路径，请核对路径与动词；目录外动作不可调用）");
    }

    /**
     * 唯一键冲突（R5-D-110）：此前落到下方兜底分支变成 {@code code=500「系统异常」}，
     * 而 {@code sys_oper_log.error_msg} 又把整段 SQL 原文带回页面（既暴露库面结构，又让
     * 调用方以为服务端坏了、去重试同一笔）。这里显式回 4090 业务可读文案——
     * SQL 原文、索引名、mapper 路径只落服务端日志，绝不出现在响应里。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest req) {
        log.warn("唯一键冲突 uri={} cause={}", req.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.DATA_ALREADY_EXISTS.getCode(),
                ErrorCode.DATA_ALREADY_EXISTS.getMessage());
    }

    /**
     * D-V4R-89：工作流引擎异常（FlowableException / BpmnModelException 等）单独接管。
     * 此前落兜底 500「系统异常，请稍后重试」，操作人无从判断是自己填错了还是系统坏了，
     * 于是反复重试同一笔——而这类故障（流程定义缺失/损坏、部署缓存为空）重试永远无效。
     * 典型现场（2026-10-16 D-V4R-88）：清库误删 {@code act_ge_bytearray}（BPMN 资源字节流），
     * 点【提交审批】报
     * {@code deployment '4e682bf3-…' didn't put process definition 'PTP02-02:1:4e6ebba6-…' in the cache}，
     * 界面只给"系统异常，请稍后重试"，数据库侧自检完全查不出（引用链完整、无孤儿记录），
     * 只有真正发起流程实例那一瞬间才炸。
     * <p>此处回 5001 专用码 + 明确指向"联系管理员"而非"重试"，避免无谓重试与误报。
     * 引擎原始异常仍完整落服务端日志，响应里不带任何 SQL / 表名 / 部署 ID。
     */
    @ExceptionHandler({org.flowable.common.engine.api.FlowableException.class,
            org.flowable.common.engine.api.FlowableOptimisticLockingException.class,
            org.flowable.common.engine.api.FlowableObjectNotFoundException.class})
    public Result<Void> handleFlowable(Exception ex, HttpServletRequest req) {
        log.error("工作流引擎异常 uri={} type={}", req.getRequestURI(), ex.getClass().getName(), ex);
        return Result.fail(ErrorCode.WORKFLOW_ENGINE_ERROR.getCode(),
                ErrorCode.WORKFLOW_ENGINE_ERROR.getMessage());
    }

    /**
     * D-DATE-01：请求体反序列化失败（日期格式、字段名、类型不匹配）→ 400 + 可执行提示。
     *
     * <p>此前这类一律落到兜底 {@link #handleOther} 返回 500「系统异常，请稍后重试」，
     * 把**调用方的参数问题**误导成系统故障。实测四例同源：
     * {@code period='2026-10'}（列宽 varchar(6)）、缺 {@code bizId}（null 解引用）、
     * {@code beginTime='2026-10-19 09:00:00'}（Jackson 只收 ISO 的 {@code T} 分隔）、
     * 缺 {@code standardUnit}（NOT NULL 列）。
     *
     * <p>现在把 Jackson 的三类解析异常单独收口，并从 message 里抽出最可能的原因提示。
     */
    @ExceptionHandler({com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
            com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public Result<Void> handleJsonParse(Exception ex, HttpServletRequest req) {
        log.warn("请求体解析失败 uri={} type={} msg={}", req.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                "请求参数格式不正确：" + parseHint(ex.getMessage()) + "；日期时间请用 ISO 格式（如 2026-10-26T18:00:00）");
    }

    /**
     * D-DATE-01：数据层「值放不进去」的异常（列太短 / 非空约束 / 类型越界）→ 400 + 可执行提示。
     *
     * <p>{@code BadSqlGrammarException} 里嵌的 {@code SQLSyntaxErrorException} 同属此类，
     * 但真正的「列不存在 / 列名写错」要交给 500 + 详细日志（那是代码缺陷不是参数问题），
     * 因此这里只兜「值不合法」三类：Data too long / cannot be null / Out of range / 精度截断。
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex,
                                             HttpServletRequest req) {
        log.warn("数据约束校验失败 uri={} msg={}", req.getRequestURI(), rootMsg(ex));
        String m = rootMsg(ex);
        String hint;
        if (m.contains("Data too long")) {
            hint = "有字段超出长度限制（常见于期间/编码类字段，本系统期间格式为 yyyyMM 六位）";
        } else if (m.contains("cannot be null")) {
            hint = "有必填字段未传（该字段在数据库为 NOT NULL）";
        } else if (m.contains("Out of range") || m.contains("Truncated incorrect")) {
            hint = "有数值超出字段允许范围或精度被截断";
        } else {
            hint = "提交的数据不满足数据库约束：" + m;
        }
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), hint);
    }

    /** 取最底层 cause 的消息（数据完整性异常的真正原因藏在 cause 链里） */
    private static String rootMsg(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** 从 Jackson 异常里挑出对调用方最有用的那一句（原始 message 含路径与类名，噪音大） */
    private static String parseHint(String raw) {
        if (raw == null) {
            return "请求体不是合法 JSON";
        }
        int at = raw.indexOf("through reference chain");
        String head = at > 0 ? raw.substring(0, at) : raw;
        head = head.replaceAll("\\s+", " ").trim();
        return head.length() > 160 ? head.substring(0, 160) + "…" : head;
    }

    /** 兜底：未预期异常，记详细日志但只回给前端模糊信息 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception ex, HttpServletRequest req) {
        log.error("系统异常 uri={}", req.getRequestURI(), ex);
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
