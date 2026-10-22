package com.fssc.masterdata.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.dto.PrintDto;
import com.fssc.masterdata.entity.MdPrintTemplate;
import com.fssc.masterdata.entity.MdPrintTemplateSet;
import com.fssc.masterdata.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印/套打引擎对外接口（V1.4.81，闭环差距②「打印无套打设计器」）。
 *
 * <p>路径 {@code /api/tenant/masterdata/print/**}。
 *
 * <p><b>前端改造点</b>：{@code fssc-web/src/composables/usePrintTemplate.js} 原先把 5 套模板
 * 写死在 TEMPLATES 常量里。改造后改为：
 * <pre>
 *   1) GET  /template/biz/{bizType}      取模板（含毫米坐标与字段属性）
 *   2) POST /render                      传数据拿渲染 HTML + 必录校验结论
 * </pre>
 * 这样新增单据打印样式＝后台建模板数据，不再改前端代码。
 */
@RestController
@RequestMapping("/api/tenant/masterdata/print")
@RequiredArgsConstructor
public class PrintTemplateController {

    private final PrintTemplateService printService;

    // ==================== 模板查询 ====================

    /** 全部模板（套打设计器模板列表页用） */
    @GetMapping("/template/list")
    public Result<List<MdPrintTemplate>> listTemplates() {
        return Result.success(printService.listAll());
    }

    /** 按业务类型取启用中的模板（打印入口用） */
    @GetMapping("/template/biz/{bizType}")
    public Result<List<PrintDto.TemplateView>> listByBiz(@PathVariable String bizType) {
        List<PrintDto.TemplateView> views = new ArrayList<>();
        for (MdPrintTemplate t : printService.listByBizType(bizType)) {
            views.add(printService.buildView(t));
        }
        return Result.success(views);
    }

    /** 按编码取单个模板完整视图 */
    @GetMapping("/template/{templateCode}")
    public Result<PrintDto.TemplateView> getByCode(@PathVariable String templateCode) {
        PrintDto.RenderRequest probe = new PrintDto.RenderRequest();
        probe.setTemplateCode(templateCode);
        probe.setRenderHtml(false);
        probe.setData(new HashMap<>());
        return Result.success(printService.render(probe).getTemplate());
    }

    // ==================== 模板套 ====================

    @GetMapping("/set/list")
    public Result<List<MdPrintTemplateSet>> listSets() {
        return Result.success(printService.listSets());
    }

    @GetMapping("/set/{setCode}")
    public Result<PrintDto.SetView> getSet(@PathVariable String setCode) {
        return Result.success(printService.buildSetView(setCode));
    }

    // ==================== 渲染 ====================

    /**
     * 渲染打印内容。
     *
     * <p>传 setCode 时按模板套渲染多个模板，返回数组；否则返回单个结果。
     * 必录字段缺失时 {@code blocked=true} 且 {@code missingRequired} 列出缺哪几个，
     * 前端据此拦截（对齐行业方案「字段必录属性」）。
     */
    @PostMapping("/render")
    public Result<Object> render(@RequestBody PrintDto.RenderRequest req) {
        if (req.getSetCode() != null && !req.getSetCode().isBlank()) {
            List<PrintDto.RenderResult> list = printService.renderBySet(req);
            return Result.success(list);
        }
        return Result.success(printService.render(req));
    }

    // ==================== 模板维护（套打设计器） ====================

    @PostMapping("/template/save")
    @RequiresPerm("md:print:template:edit")
    @OperLog(module = "打印模板", operType = "UPDATE", desc = "保存打印模板（含字段项全量覆盖）")
    public Result<Long> save(@RequestBody PrintDto.SaveTemplateRequest req) {
        return Result.success(printService.saveTemplate(req));
    }

    @PostMapping("/template/delete/{templateId}")
    @RequiresPerm("md:print:template:edit")
    @OperLog(module = "打印模板", operType = "DELETE", desc = "删除打印模板")
    public Result<Void> delete(@PathVariable Long templateId) {
        printService.deleteTemplate(templateId);
        return Result.success();
    }
}
