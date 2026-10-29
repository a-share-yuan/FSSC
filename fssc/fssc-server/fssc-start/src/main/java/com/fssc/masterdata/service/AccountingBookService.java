// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.GlAccountingBook;
import com.fssc.masterdata.entity.GlBookSubjectMap;
import com.fssc.masterdata.entity.GlCurrency;
import com.fssc.masterdata.entity.GlStandard;
import com.fssc.masterdata.mapper.GlAccountingBookMapper;
import com.fssc.masterdata.mapper.GlBookSubjectMapMapper;
import com.fssc.masterdata.mapper.GlCurrencyMapper;
import com.fssc.masterdata.mapper.GlStandardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多账簿服务——闭环差距③「无账簿/准则/多本位币体系（多账簿零代码）」。
 *
 * <p><b>参考规则逐条落地</b>：
 * <ul>
 *   <li>「账簿类型分为主账簿和副账簿，一个核算组织在同一核算体系内仅能存在唯一的主账簿」
 *       → {@link #createBook} 显式校验 + {@code uk_gab_main} 唯一索引<b>双层兜底</b>
 *       应用层给可读报错，数据库层防并发穿透 ;</li>
 *   <li>「按不同的会计准则与制度出具多套财务核算数据」
 *       → 同一 company 可在多个 standard 下各持一本 MAIN 账簿，
 *       {@link #listBooksOf} 按公司列出全部账簿即「多套核算数据」的载体;</li>
 *   <li>多本位币 → 账簿带 {@code baseCurrencyId}，
 *       {@link #mapSubject} 做准则间科目归一。</li>
 * </ul>
 *
 * <p><b>为什么约束要落在数据库</b>：「仅能存在唯一主账簿」是会计规则，
 * 如果只在应用层判断，两人并发建账必穿透 ⇒ 必须有唯一索引兜底。
 * 本类的存在校验只为<b>给出可读错误信息</b>，不是唯一防线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingBookService {

    private final GlAccountingBookMapper bookMapper;
    private final GlStandardMapper standardMapper;
    private final GlCurrencyMapper currencyMapper;
    private final GlBookSubjectMapMapper subjectMapMapper;
    /** 只用于按 id 批量回填名称（避免在服务里硬编码 JDBC 凭据） */
    private final JdbcTemplate jdbcTemplate;

    // 查询

    public List<GlStandard> listStandards() {
        return standardMapper.selectList(new LambdaQueryWrapper<GlStandard>()
                .eq(GlStandard::getStatus, "ENABLED")
                .orderByAsc(GlStandard::getStandardCode));
    }

    public List<GlCurrency> listCurrencies() {
        return currencyMapper.selectList(new LambdaQueryWrapper<GlCurrency>()
                .eq(GlCurrency::getStatus, "ENABLED")
                .orderByDesc(GlCurrency::getIsBase)
                .orderByAsc(GlCurrency::getCurrencyCode));
    }

    public List<GlAccountingBook> listBooks(Integer year, String bookType) {
        LambdaQueryWrapper<GlAccountingBook> w = new LambdaQueryWrapper<GlAccountingBook>()
                .orderByAsc(GlAccountingBook::getCompanyId)
                .orderByAsc(GlAccountingBook::getStandardId)
                .orderByAsc(GlAccountingBook::getBookType);
        if (year != null) {
            w.eq(GlAccountingBook::getAccountingYear, year);
        }
        if (bookType != null && !bookType.isBlank()) {
            w.eq(GlAccountingBook::getBookType, bookType);
        }
        return bookMapper.selectList(w);
    }

    /** 按核算组织列出全部账簿（多准则 ⇒ 可能多本主账簿） */
    public List<GlAccountingBook> listBooksOf(Long companyId, Integer year) {
        return bookMapper.selectList(new LambdaQueryWrapper<GlAccountingBook>()
                .eq(GlAccountingBook::getCompanyId, companyId)
                .eq(year != null, GlAccountingBook::getAccountingYear, year)
                .orderByAsc(GlAccountingBook::getStandardId)
                .orderByAsc(GlAccountingBook::getBookType));
    }

    public GlAccountingBook getById(Long bookId) {
        GlAccountingBook b = bookMapper.selectById(bookId);
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账簿不存在: " + bookId);
        }
        return b;
    }

    /**
     * 按「核算组织+准则」定位账簿（记账时的默认账簿）。
     */
    public GlAccountingBook resolveBook(Long companyId, Long standardId, Integer year) {
        GlAccountingBook b = bookMapper.selectOne(new LambdaQueryWrapper<GlAccountingBook>()
                .eq(GlAccountingBook::getCompanyId, companyId)
                .eq(GlAccountingBook::getStandardId, standardId)
                .eq(GlAccountingBook::getAccountingYear, year)
                .eq(GlAccountingBook::getBookType, "MAIN")
                .last("LIMIT 1"));
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该核算组织在此准则下尚未建主账簿: company=" + companyId
                    + " standard=" + standardId + " year=" + year);
        }
        return b;
    }

    // 建账

    /**
     * 建账。
     *
     * <p><b>约束双层兜底</b>：
     * <ol>
     *   <li>本方法显式检查「同一核算组织+准则+年度+类型」是否已有账簿，给出可读报错；</li>
     *   <li>{@code uk_gab_main} 唯一索引防并发穿透 —— 两个请求同时进来，
     *       一个成功一个撞 1062，转成 BizException 后用户看到的是同一句话。</li>
     * </ol>
     * 注意：同一核算组织在<b>不同准则</b>下建 MAIN 账簿是<b>允许</b>的
     * 按不同准则出具多套核算数据。
     */
    public Long createBook(GlAccountingBook book) {
        if (book.getCompanyId() == null || book.getStandardId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "核算组织与会计准则不能为空");
        }
        if (book.getAccountingYear() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计年度不能为空");
        }
        String type = Objects.requireNonNullElse(book.getBookType(), "MAIN");
        book.setBookType(type);

        Long dup = bookMapper.selectCount(new LambdaQueryWrapper<GlAccountingBook>()
                .eq(GlAccountingBook::getCompanyId, book.getCompanyId())
                .eq(GlAccountingBook::getStandardId, book.getStandardId())
                .eq(GlAccountingBook::getAccountingYear, book.getAccountingYear())
                .eq(GlAccountingBook::getBookType, type));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS,
                    "同一核算组织在同一准则与年度下已存在" + ("MAIN".equals(type) ? "主" : "副")
                    + "账簿，请勿重复创建");
        }
        try {
            bookMapper.insert(book);
        } catch (DuplicateKeyException e) {
            // 并发穿透由唯一索引兜住，转成同一规则的业务错误
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS,
                    "同一核算组织在同一准则与年度下已存在" + ("MAIN".equals(type) ? "主" : "副")
                    + "账簿，请勿重复创建");
        }
        log.info("[gl-book] 建账 code={} company={} standard={} year={} type={}",
                book.getBookCode(), book.getCompanyId(), book.getStandardId(),
                book.getAccountingYear(), type);
        return book.getId();
    }

    // 科目映射

    /** 取账簿的科目映射表（key=本准则科目ID，value=通用科目ID） */
    public Map<Long, Long> subjectMapOf(Long bookId) {
        List<GlBookSubjectMap> list = subjectMapMapper.selectList(
                new LambdaQueryWrapper<GlBookSubjectMap>()
                        .eq(GlBookSubjectMap::getBookId, bookId)
                        .eq(GlBookSubjectMap::getStatus, "ENABLED"));
        Map<Long, Long> map = new HashMap<>(list.size());
        for (GlBookSubjectMap m : list) {
            if (m.getCommonSubjectId() != null && m.getLocalSubjectId() != null) {
                map.put(m.getLocalSubjectId(), m.getCommonSubjectId());
            }
        }
        return map;
    }

    /**
     * 按准则归一科目：把本准则科目映射到通用科目。
     *
     * <p>「按不同会计准则出具多套核算数据」的落点：同一笔业务在不同准则账簿下
     * 落到各自规则的科目上，跨账簿汇总时用 commonSubjectId 归一。
     */
    public Long mapSubject(Long bookId, Long localSubjectId) {
        if (localSubjectId == null) {
            return null;
        }
        return subjectMapOf(bookId).getOrDefault(localSubjectId, localSubjectId);
    }

    /** 保存科目映射 */
    public Long saveSubjectMap(GlBookSubjectMap m) {
        if (m.getBookId() == null || m.getLocalSubjectId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账簿与本准则科目不能为空");
        }
        GlBookSubjectMap exist = subjectMapMapper.selectOne(
                new LambdaQueryWrapper<GlBookSubjectMap>()
                        .eq(GlBookSubjectMap::getBookId, m.getBookId())
                        .eq(GlBookSubjectMap::getLocalSubjectId, m.getLocalSubjectId())
                        .last("LIMIT 1"));
        if (exist == null) {
            subjectMapMapper.insert(m);
        } else {
            m.setId(exist.getId());
            subjectMapMapper.updateById(m);
        }
        return m.getId();
    }

    // 多账簿总览

    /**
     * 多账簿总览：按核算组织聚合，展示其名下各准则/各类型的账簿。
     * 报表页「按不同会计准则出具多套财务核算数据」的数据源。
     */
    public List<Map<String, Object>> overview(Integer year) {
        List<GlAccountingBook> books = listBooks(year, null);
        if (books.isEmpty()) {
            return List.of();
        }
        Set<Long> companyIds = books.stream().map(GlAccountingBook::getCompanyId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> stdIds = books.stream().map(GlAccountingBook::getStandardId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> curIds = books.stream().map(GlAccountingBook::getBaseCurrencyId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> compNames = new HashMap<>();
        Map<Long, String> stdNames = new HashMap<>();
        Map<Long, String> curNames = new HashMap<>();
        jdbcNames(compNames, stdNames, curNames, companyIds, stdIds, curIds);

        Map<Long, List<GlAccountingBook>> byCompany = books.stream()
                .collect(Collectors.groupingBy(GlAccountingBook::getCompanyId,
                        LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> out = new ArrayList<>(byCompany.size());
        byCompany.forEach((cid, list) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyId", cid);
            row.put("companyName", compNames.getOrDefault(cid, "公司#" + cid));
            row.put("bookCount", list.size());
            row.put("mainBookCount", list.stream()
                    .filter(b -> "MAIN".equals(b.getBookType())).count());
            row.put("subBookCount", list.stream()
                    .filter(b -> "SUB".equals(b.getBookType())).count());
            row.put("standards", list.stream()
                    .map(b -> stdNames.getOrDefault(b.getStandardId(), "准则#" + b.getStandardId()))
                    .distinct().collect(Collectors.toList()));
            row.put("currencies", list.stream()
                    .map(b -> curNames.getOrDefault(b.getBaseCurrencyId(), "-"))
                    .distinct().collect(Collectors.toList()));
            row.put("books", list);
            out.add(row);
        });
        return out;
    }

    private void jdbcNames(Map<Long, String> compNames, Map<Long, String> stdNames,
                           Map<Long, String> curNames, Set<Long> companyIds,
                           Set<Long> stdIds, Set<Long> curIds) {
        fillNames(companyIds, compNames, "sys_company", "id", "company_name");
        fillNames(stdIds, stdNames, "gl_standard", "id", "standard_name");
        fillNames(curIds, curNames, "gl_currency", "id", "currency_code");
    }

    private void fillNames(Set<Long> ids, Map<Long, String> target,
                           String table, String idCol, String nameCol) {
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
        try {
            // 用 queryForList 而非 query(RowCallbackHandler)：后者在无参 query 里会走
            // 「忽略重载」路径，拿不到 ResultSet，导致名称静默回填不上（踩过一次：
            // overview 返回「公司#1 / 准则#4 / -」而库里明明有名字）。
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT `" + idCol + "` AS nid, `" + nameCol + "` AS nname FROM `" + table
                            + "` WHERE `" + idCol + "` IN (" + in + ")");
            for (Map<String, Object> r : rows) {
                Object id = r.get("nid");
                Object nm = r.get("nname");
                if (id instanceof Number n && nm != null) {
                    target.put(n.longValue(), String.valueOf(nm));
                }
            }
        } catch (Exception e) {
            log.warn("[gl-book] 名称回填失败 table={} : {}", table, e.getMessage());
        }
    }
}
