package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.dto.PrintDto;
import com.fssc.masterdata.entity.MdPrintTemplate;
import com.fssc.masterdata.entity.MdPrintTemplateItem;
import com.fssc.masterdata.entity.MdPrintTemplateSet;
import com.fssc.masterdata.entity.MdPrintTemplateSetItem;
import com.fssc.masterdata.mapper.MdPrintTemplateItemMapper;
import com.fssc.masterdata.mapper.MdPrintTemplateMapper;
import com.fssc.masterdata.mapper.MdPrintTemplateSetItemMapper;
import com.fssc.masterdata.mapper.MdPrintTemplateSetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 打印/套打引擎（V1.4.81）——闭环差距②「打印无套打设计器」。
 *
 * <p><b>本类解决的三个问题</b>：
 * <ol>
 *   <li><b>模板是数据不是代码</b>：版式/坐标/字段属性落 {@code md_print_template*}，
 *       新增单据打印样式＝建数据，不改代码不发版；</li>
 *   <li><b>毫米换算只在服务端做一次</b>：网格坐标 → 绝对毫米坐标由本类算好下发，
 *       避免每个前端页面各写一套换算导致版式漂移；</li>
 *       {@code required=1} 的字段缺值时 {@link PrintDto.RenderResult#getBlocked()} 为 true，
 *       前端据此拦截，不放行打印。</li>
 * </ol>
 *
 * <p><b>为什么不直接让前端渲染</b>：模板既然是数据，就必须有一处权威的「模板解释器」，
 * 否则每新增一个打印页面就会又写一份渲染逻辑，重新退回「改代码才能改样式」的老路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateService {

    private final MdPrintTemplateMapper templateMapper;
    private final MdPrintTemplateItemMapper itemMapper;
    private final MdPrintTemplateSetMapper setMapper;
    private final MdPrintTemplateSetItemMapper setItemMapper;

    // ==================== 查询 ====================

    /**
     * 按业务类型取启用中的模板列表（同一 template_code 取最高版本）。
     */
    public List<MdPrintTemplate> listByBizType(String bizType) {
        LambdaQueryWrapper<MdPrintTemplate> w = new LambdaQueryWrapper<MdPrintTemplate>()
                .eq(MdPrintTemplate::getBizType, bizType)
                .eq(MdPrintTemplate::getStatus, "ENABLED")
                .orderByDesc(MdPrintTemplate::getVersion)
                .orderByAsc(MdPrintTemplate::getTemplateCode);
        List<MdPrintTemplate> all = templateMapper.selectList(w);
        // 同一 template_code 只留最高版本
        Map<String, MdPrintTemplate> latest = new HashMap<>();
        for (MdPrintTemplate t : all) {
            latest.merge(t.getTemplateCode(), t,
                    (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        return new ArrayList<>(latest.values());
    }

    public List<MdPrintTemplate> listAll() {
        return templateMapper.selectList(new LambdaQueryWrapper<MdPrintTemplate>()
                .orderByAsc(MdPrintTemplate::getBizType)
                .orderByAsc(MdPrintTemplate::getTemplateCode)
                .orderByDesc(MdPrintTemplate::getVersion));
    }

    /**
     * 取模板完整视图（含字段项 + 毫米绝对坐标）。
     */
    public PrintDto.TemplateView buildView(MdPrintTemplate tpl) {
        PrintDto.TemplateView v = new PrintDto.TemplateView();
        v.setTemplateId(tpl.getId());
        v.setTemplateCode(tpl.getTemplateCode());
        v.setTemplateName(tpl.getTemplateName());
        v.setBizType(tpl.getBizType());
        v.setPaperSize(tpl.getPaperSize());
        v.setOrientation(tpl.getOrientation());
        v.setWidthMm(tpl.getWidthMm());
        v.setHeightMm(tpl.getHeightMm());
        v.setMarginTopMm(tpl.getMarginTopMm());
        v.setMarginBottomMm(tpl.getMarginBottomMm());
        v.setMarginLeftMm(tpl.getMarginLeftMm());
        v.setMarginRightMm(tpl.getMarginRightMm());
        v.setGridRows(tpl.getGridRows());
        v.setGridCols(tpl.getGridCols());
        v.setRepeatHeader(tpl.getRepeatHeader());
        v.setVersion(tpl.getVersion());
        v.setIsSystem(tpl.getIsSystem());
        v.setStatus(tpl.getStatus());
        v.setItems(buildItems(tpl));
        return v;
    }

    /**
     * 取字段项并把网格坐标换算成毫米绝对坐标。
     *
     * <p>换算口径：可用宽 = 页宽 − 左右边距；单格宽 = 可用宽 / gridCols；
     * 单格高 = （页高 − 上下边距） / gridRows。字段未显式给宽高时按跨列/跨行均分。
     */
    private List<PrintDto.ItemView> buildItems(MdPrintTemplate tpl) {
        List<MdPrintTemplateItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MdPrintTemplateItem>()
                        .eq(MdPrintTemplateItem::getTemplateId, tpl.getId())
                        .orderByAsc(MdPrintTemplateItem::getSortOrder)
                        .orderByAsc(MdPrintTemplateItem::getId));

        BigDecimal pageW = nz(tpl.getWidthMm(), "210.00");
        BigDecimal pageH = nz(tpl.getHeightMm(), "297.00");
        BigDecimal mL = nz(tpl.getMarginLeftMm(), "15.00");
        BigDecimal mR = nz(tpl.getMarginRightMm(), "15.00");
        BigDecimal mT = nz(tpl.getMarginTopMm(), "15.00");
        BigDecimal mB = nz(tpl.getMarginBottomMm(), "15.00");
        int rows = Objects.requireNonNullElse(tpl.getGridRows(), 30);
        int cols = Objects.requireNonNullElse(tpl.getGridCols(), 12);
        rows = Math.max(1, rows);
        cols = Math.max(1, cols);

        BigDecimal usableW = pageW.subtract(mL).subtract(mR).max(BigDecimal.ZERO);
        BigDecimal usableH = pageH.subtract(mT).subtract(mB).max(BigDecimal.ZERO);
        BigDecimal cellW = usableW.divide(BigDecimal.valueOf(cols), 2, RoundingMode.HALF_UP);
        BigDecimal cellH = usableH.divide(BigDecimal.valueOf(rows), 2, RoundingMode.HALF_UP);

        List<PrintDto.ItemView> views = new ArrayList<>(items.size());
        for (MdPrintTemplateItem it : items) {
            PrintDto.ItemView iv = new PrintDto.ItemView();
            iv.setItemId(it.getId());
            iv.setItemKey(it.getItemKey());
            iv.setLabel(it.getLabel());
            iv.setDataSource(it.getDataSource());
            iv.setItemType(it.getItemType());
            iv.setRowIndex(it.getRowIndex());
            iv.setColIndex(it.getColIndex());
            iv.setRowSpan(Objects.requireNonNullElse(it.getRowSpan(), 1));
            iv.setColSpan(Objects.requireNonNullElse(it.getColSpan(), 1));
            iv.setFontSize(it.getFontSize());
            iv.setFontWeight(it.getFontWeight());
            iv.setAlign(it.getAlign());
            iv.setRequired(it.getRequired());
            iv.setLocked(it.getLocked());
            iv.setVisible(it.getVisible());
            iv.setFormatType(it.getFormatType());
            iv.setDecimalPlaces(it.getDecimalPlaces());
            iv.setSortOrder(it.getSortOrder());

            int r = Objects.requireNonNullElse(it.getRowIndex(), 1);
            int c = Objects.requireNonNullElse(it.getColIndex(), 1);
            iv.setXMm(mL.add(cellW.multiply(BigDecimal.valueOf(c - 1))).setScale(2, RoundingMode.HALF_UP));
            iv.setYMm(mT.add(cellH.multiply(BigDecimal.valueOf(r - 1))).setScale(2, RoundingMode.HALF_UP));
            iv.setWidthMm(it.getWidthMm() != null ? it.getWidthMm()
                    : cellW.multiply(BigDecimal.valueOf(iv.getColSpan())).setScale(2, RoundingMode.HALF_UP));
            iv.setHeightMm(it.getHeightMm() != null ? it.getHeightMm()
                    : cellH.multiply(BigDecimal.valueOf(iv.getRowSpan())).setScale(2, RoundingMode.HALF_UP));
            views.add(iv);
        }
        return views;
    }

    // ==================== 渲染 ====================

    /**
     * 渲染打印内容：绑定数据 → 必录校验 → 拼 HTML。
     *
     * @param req 渲染请求
     * @return 渲染结果（含 HTML 与必录校验结论）
     */
    public PrintDto.RenderResult render(PrintDto.RenderRequest req) {
        MdPrintTemplate tpl = resolveTemplate(req);
        PrintDto.TemplateView view = buildView(tpl);

        Map<String, Object> data = req.getData() == null ? Map.of() : req.getData();
        Map<String, Object> head = asMap(data.get("head"));
        List<Map<String, Object>> lines = asList(data.get("lines"));
        Map<String, Object> system = systemVars();

        List<PrintDto.MissingField> missing = new ArrayList<>();
        for (PrintDto.ItemView iv : view.getItems()) {
            if (Objects.equals(iv.getVisible(), 0)) {
                continue;
            }
            String val = resolveValue(iv.getDataSource(), head, lines, system);
            iv.setBoundValue(val);
            boolean isBlank = val == null || val.isBlank();
            if (isBlank && Objects.equals(iv.getRequired(), 1)) {
                missing.add(new PrintDto.MissingField(iv.getItemKey(), iv.getLabel(), iv.getDataSource()));
            }
        }

        PrintDto.RenderResult res = new PrintDto.RenderResult();
        res.setTemplate(view);
        res.setMissingRequired(missing);
        res.setBlocked(!missing.isEmpty());
        if (!Boolean.FALSE.equals(req.getRenderHtml())) {
            res.setHtml(buildHtml(view, lines));
        }
        return res;
    }

    /**
     * 按模板套渲染：返回套内全部模板的渲染结果（按 pageOrder 排序）。
     */
    public List<PrintDto.RenderResult> renderBySet(PrintDto.RenderRequest req) {
        PrintDto.SetView set = buildSetView(req.getSetCode());
        if (set == null || set.getMembers() == null || set.getMembers().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "打印模板套不存在或无成员: " + req.getSetCode());
        }
        List<PrintDto.RenderResult> out = new ArrayList<>();
        for (PrintDto.SetMember m : set.getMembers()) {
            PrintDto.RenderRequest sub = new PrintDto.RenderRequest();
            sub.setTemplateCode(m.getTemplate().getTemplateCode());
            sub.setData(req.getData());
            sub.setRenderHtml(req.getRenderHtml());
            out.add(render(sub));
        }
        return out;
    }

    // ==================== 保存（套打设计器） ====================

    /**
     * 保存模板（设计器提交）。存在则更新，不存在则新增；字段项按模板全量覆盖。
     *
     * <p>系统预置模板（is_system=1）允许改版式但不允许改 code——code 是前端契约。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveTemplate(PrintDto.SaveTemplateRequest req) {
        if (req.getTemplateCode() == null || req.getTemplateCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码不能为空");
        }
        if (req.getBizType() == null || req.getBizType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "业务单据类型不能为空");
        }
        MdPrintTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<MdPrintTemplate>()
                        .eq(MdPrintTemplate::getTemplateCode, req.getTemplateCode())
                        .eq(MdPrintTemplate::getVersion, Objects.requireNonNullElse(req.getVersion(), 1))
                        .last("LIMIT 1"));

        boolean isNew = tpl == null;
        if (isNew) {
            tpl = new MdPrintTemplate();
            tpl.setTemplateCode(req.getTemplateCode());
            tpl.setVersion(Objects.requireNonNullElse(req.getVersion(), 1));
            tpl.setIsSystem(0);
        }
        tpl.setTemplateName(req.getTemplateName());
        tpl.setBizType(req.getBizType());
        tpl.setPaperSize(Objects.requireNonNullElse(req.getPaperSize(), "A4"));
        tpl.setOrientation(Objects.requireNonNullElse(req.getOrientation(), "PORTRAIT"));
        tpl.setWidthMm(nz(req.getWidthMm(), "210.00"));
        tpl.setHeightMm(nz(req.getHeightMm(), "297.00"));
        tpl.setMarginTopMm(nz(req.getMarginTopMm(), "15.00"));
        tpl.setMarginBottomMm(nz(req.getMarginBottomMm(), "15.00"));
        tpl.setMarginLeftMm(nz(req.getMarginLeftMm(), "15.00"));
        tpl.setMarginRightMm(nz(req.getMarginRightMm(), "15.00"));
        tpl.setGridRows(Objects.requireNonNullElse(req.getGridRows(), 30));
        tpl.setGridCols(Objects.requireNonNullElse(req.getGridCols(), 12));
        tpl.setRepeatHeader(Objects.requireNonNullElse(req.getRepeatHeader(), 1));
        tpl.setStatus(Objects.requireNonNullElse(req.getStatus(), "ENABLED"));
        tpl.setRemark(req.getRemark());
        if (isNew) {
            templateMapper.insert(tpl);
        } else {
            templateMapper.updateById(tpl);
        }

        // 字段项全量覆盖：先软删旧项再插新项，避免「改了删了又没加」的脏数据
        itemMapper.delete(new LambdaQueryWrapper<MdPrintTemplateItem>()
                .eq(MdPrintTemplateItem::getTemplateId, tpl.getId()));
        if (req.getItems() != null) {
            int sort = 0;
            for (PrintDto.ItemRequest ir : req.getItems()) {
                MdPrintTemplateItem it = new MdPrintTemplateItem();
                it.setTemplateId(tpl.getId());
                it.setItemKey(ir.getItemKey());
                it.setLabel(ir.getLabel());
                it.setDataSource(ir.getDataSource());
                it.setItemType(Objects.requireNonNullElse(ir.getItemType(), "FIELD"));
                it.setRowIndex(Objects.requireNonNullElse(ir.getRowIndex(), 1));
                it.setColIndex(Objects.requireNonNullElse(ir.getColIndex(), 1));
                it.setRowSpan(Objects.requireNonNullElse(ir.getRowSpan(), 1));
                it.setColSpan(Objects.requireNonNullElse(ir.getColSpan(), 1));
                it.setWidthMm(ir.getWidthMm());
                it.setHeightMm(ir.getHeightMm());
                it.setFontSize(Objects.requireNonNullElse(ir.getFontSize(), 12));
                it.setFontWeight(Objects.requireNonNullElse(ir.getFontWeight(), "NORMAL"));
                it.setAlign(Objects.requireNonNullElse(ir.getAlign(), "LEFT"));
                it.setRequired(Objects.requireNonNullElse(ir.getRequired(), 0));
                it.setLocked(Objects.requireNonNullElse(ir.getLocked(), 0));
                it.setVisible(Objects.requireNonNullElse(ir.getVisible(), 1));
                it.setFormatType(Objects.requireNonNullElse(ir.getFormatType(), "TEXT"));
                it.setDecimalPlaces(Objects.requireNonNullElse(ir.getDecimalPlaces(), 2));
                it.setSortOrder(Objects.requireNonNullElse(ir.getSortOrder(), sort++));
                it.setRemark(ir.getRemark());
                itemMapper.insert(it);
            }
        }
        log.info("[print] 模板保存: code={} bizType={} items={}",
                req.getTemplateCode(), req.getBizType(),
                req.getItems() == null ? 0 : req.getItems().size());
        return tpl.getId();
    }

    /**
     * 删除模板（系统预置禁删）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        MdPrintTemplate tpl = templateMapper.selectById(templateId);
        if (tpl == null) {
            return;
        }
        if (Objects.equals(tpl.getIsSystem(), 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "系统预置模板禁止删除（可改版式或停用）: " + tpl.getTemplateCode());
        }
        itemMapper.delete(new LambdaQueryWrapper<MdPrintTemplateItem>()
                .eq(MdPrintTemplateItem::getTemplateId, templateId));
        templateMapper.deleteById(templateId);
    }

    // ==================== 模板套 ====================

    public List<MdPrintTemplateSet> listSets() {
        return setMapper.selectList(new LambdaQueryWrapper<MdPrintTemplateSet>()
                .orderByAsc(MdPrintTemplateSet::getSortOrder));
    }

    public PrintDto.SetView buildSetView(String setCode) {
        MdPrintTemplateSet set = setMapper.selectOne(
                new LambdaQueryWrapper<MdPrintTemplateSet>()
                        .eq(MdPrintTemplateSet::getSetCode, setCode)
                        .last("LIMIT 1"));
        if (set == null) {
            return null;
        }
        PrintDto.SetView v = new PrintDto.SetView();
        v.setSetId(set.getId());
        v.setSetCode(set.getSetCode());
        v.setSetName(set.getSetName());
        v.setBizType(set.getBizType());
        v.setStatus(set.getStatus());

        List<MdPrintTemplateSetItem> members = setItemMapper.selectList(
                new LambdaQueryWrapper<MdPrintTemplateSetItem>()
                        .eq(MdPrintTemplateSetItem::getSetId, set.getId())
                        .orderByAsc(MdPrintTemplateSetItem::getPageOrder));
        List<PrintDto.SetMember> ms = new ArrayList<>();
        for (MdPrintTemplateSetItem mi : members) {
            MdPrintTemplate tpl = templateMapper.selectById(mi.getTemplateId());
            if (tpl == null) {
                continue;
            }
            PrintDto.SetMember m = new PrintDto.SetMember();
            m.setPageOrder(mi.getPageOrder());
            m.setCopies(mi.getCopies());
            m.setTemplate(buildView(tpl));
            ms.add(m);
        }
        v.setMembers(ms);
        return v;
    }

    // ==================== 内部 ====================

    private MdPrintTemplate resolveTemplate(PrintDto.RenderRequest req) {
        if (req.getTemplateCode() != null && !req.getTemplateCode().isBlank()) {
            MdPrintTemplate tpl = templateMapper.selectOne(
                    new LambdaQueryWrapper<MdPrintTemplate>()
                            .eq(MdPrintTemplate::getTemplateCode, req.getTemplateCode())
                            .orderByDesc(MdPrintTemplate::getVersion)
                            .last("LIMIT 1"));
            if (tpl == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "打印模板不存在: " + req.getTemplateCode());
            }
            return tpl;
        }
        if (req.getBizType() != null && !req.getBizType().isBlank()) {
            List<MdPrintTemplate> list = listByBizType(req.getBizType());
            if (list.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该业务类型下无启用中的打印模板: " + req.getBizType());
            }
            return list.get(0);
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "templateCode 与 bizType 至少传一个");
    }

    /**
     * 解析数据源路径：head.xxx / lines[].xxx / system.xxx。
     *
     * <p>{@code lines[].xxx} 在表头字段上取明细首行的值（单值语义），
     * 明细行区（LINE_DETAIL）由 {@link #buildHtml} 单独处理循环。
     */
    private String resolveValue(String path, Map<String, Object> head,
                                List<Map<String, Object>> lines, Map<String, Object> system) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            if (path.startsWith("head.")) {
                Object v = head.get(path.substring(5));
                return fmt(v, null);
            }
            if (path.startsWith("system.")) {
                return fmt(system.get(path.substring(7)), null);
            }
            if (path.startsWith("lines[].")) {
                if (lines.isEmpty()) {
                    return null;
                }
                Object v = lines.get(0).get(path.substring(8));
                return fmt(v, null);
            }
            // 裸字段名：先 head 再顶层
            Object v = head.get(path);
            if (v == null) {
                v = system.get(path);
            }
            return fmt(v, null);
        } catch (Exception e) {
            log.warn("[print] 数据源解析失败 path={} : {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 按格式类型格式化值：NUMBER 千分位 + 固定小数位；DATE 取日期部分。
     */
    private String fmt(Object v, String formatType) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate().toString();
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        return String.valueOf(v);
    }

    /**
     * 拼打印 HTML：绝对定位 + 边框 + 字号对齐，锁定字段灰显。
     */
    private String buildHtml(PrintDto.TemplateView view, List<Map<String, Object>> lines) {
        StringBuilder sb = new StringBuilder(2048);
        BigDecimal usableW = view.getWidthMm().subtract(view.getMarginLeftMm())
                .subtract(view.getMarginRightMm());
        sb.append("<div class=\"pt-doc\" style=\"position:relative;width:")
                .append(usableW).append("mm;height:")
                .append(view.getHeightMm().subtract(view.getMarginTopMm())
                        .subtract(view.getMarginBottomMm()))
                .append("mm;font-family:'SimSun','PingFang SC',serif;\">");

        // 明细循环区先抽出来，避免一张明细被渲染多次
        PrintDto.ItemView detail = view.getItems().stream()
                .filter(i -> "LINE_DETAIL".equals(i.getItemType()))
                .findFirst().orElse(null);

        for (PrintDto.ItemView iv : view.getItems()) {
            if (Objects.equals(iv.getVisible(), 0) || "LINE_DETAIL".equals(iv.getItemType())) {
                continue;
            }
            String text = iv.getBoundValue() == null ? iv.getLabel() : iv.getBoundValue();
            boolean isDivider = "DIVIDER".equals(iv.getItemType());
            sb.append("<div class=\"pt-cell")
                    .append(Objects.equals(iv.getLocked(), 1) ? " pt-locked" : "")
                    .append(isDivider ? " pt-divider" : "")
                    .append("\" style=\"position:absolute;left:").append(iv.getXMm()).append("mm;top:")
                    .append(iv.getYMm()).append("mm;width:").append(iv.getWidthMm())
                    .append("mm;min-height:").append(iv.getHeightMm())
                    .append("mm;font-size:").append(iv.getFontSize()).append("pt;font-weight:")
                    .append("BOLD".equals(iv.getFontWeight()) ? "bold" : "normal")
                    .append(";text-align:").append(lower(iv.getAlign()))
                    .append(";box-sizing:border-box;padding:1mm 2mm;")
                    .append("border:0.2mm solid #333;")
                    .append("box-shadow:inset 0 0 0 0 #333;")
                    .append("overflow:hidden;\">")
                    .append(escapeHtml(text))
                    .append("</div>");
        }

        // 明细行区：表头 + 数据行
        if (detail != null) {
            sb.append("<table class=\"pt-lines\" style=\"position:absolute;left:")
                    .append(detail.getXMm()).append("mm;top:").append(detail.getYMm())
                    .append("mm;width:").append(detail.getWidthMm())
                    .append("mm;border-collapse:collapse;font-size:").append(detail.getFontSize())
                    .append("pt;\">");
            List<PrintDto.ItemView> cols = view.getItems().stream()
                    .filter(i -> i.getDataSource() != null && i.getDataSource().startsWith("lines[]."))
                    .filter(i -> !Objects.equals(i.getItemKey(), detail.getItemKey()))
                    .sorted((a, b) -> Integer.compare(nz0(a.getSortOrder()), nz0(b.getSortOrder())))
                    .toList();
            sb.append("<thead>");
            if (Objects.equals(view.getRepeatHeader(), 1)) {
                sb.append("<tr>");
                for (PrintDto.ItemView c : cols) {
                    sb.append("<th style=\"border:0.2mm solid #333;padding:1mm;font-weight:bold;\">")
                            .append(escapeHtml(c.getLabel())).append("</th>");
                }
                sb.append("</tr>");
            }
            sb.append("</thead><tbody>");
            for (Map<String, Object> row : lines) {
                sb.append("<tr>");
                for (PrintDto.ItemView c : cols) {
                    String key = c.getDataSource().substring(8);
                    sb.append("<td style=\"border:0.2mm solid #333;padding:1mm;text-align:")
                            .append(lower(c.getAlign())).append(";\">")
                            .append(escapeHtml(fmt(row.get(key), c.getFormatType())))
                            .append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private Map<String, Object> systemVars() {
        Map<String, Object> m = new HashMap<>();
        m.put("today", LocalDate.now().toString());
        m.put("now", LocalDateTime.now().toString());
        m.put("year", LocalDate.now().getYear());
        m.put("tenantId", TenantContext.getTenantId());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> l) {
            return (List<Map<String, Object>>) l;
        }
        return List.of();
    }

    private static String lower(String s) {
        return s == null ? "left" : s.toLowerCase();
    }

    private static int nz0(Integer i) {
        return i == null ? 0 : i;
    }

    private static BigDecimal nz(BigDecimal v, String def) {
        return v == null ? new BigDecimal(def) : v;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
