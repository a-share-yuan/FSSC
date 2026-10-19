package com.fssc.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.security.UserContext;
import com.fssc.report.entity.RptReportDef;
import com.fssc.report.entity.RptUserFav;
import com.fssc.report.mapper.RptReportDefMapper;
import com.fssc.report.mapper.RptUserFavMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表目录服务：预置报表注册表管理 + 用户收藏管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCatalogService {

    private final RptReportDefMapper reportDefMapper;
    private final RptUserFavMapper userFavMapper;

    // ---------------- 报表目录 ----------------

    /** 按分类列出启用报表（category 为 null 时返回全部） */
    public List<RptReportDef> listByCategory(String category) {
        LambdaQueryWrapper<RptReportDef> qw = new LambdaQueryWrapper<>();
        qw.eq(RptReportDef::getStatus, "ACTIVE");
        if (category != null && !category.isBlank()) {
            qw.eq(RptReportDef::getCategory, category.toUpperCase());
        }
        qw.orderByAsc(RptReportDef::getSortOrder);
        return reportDefMapper.selectList(qw);
    }

    /** 按编码查报表定义 */
    public RptReportDef getByCode(String reportCode) {
        LambdaQueryWrapper<RptReportDef> qw = new LambdaQueryWrapper<>();
        qw.eq(RptReportDef::getReportCode, reportCode);
        return reportDefMapper.selectOne(qw);
    }

    // ---------------- 用户收藏 ----------------

    /** 当前用户的收藏报表列表 */
    public List<RptReportDef> myFavorites() {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<RptUserFav> qw = new LambdaQueryWrapper<>();
        qw.eq(RptUserFav::getUserId, userId);
        qw.orderByAsc(RptUserFav::getSortOrder);
        List<RptUserFav> favs = userFavMapper.selectList(qw);
        if (favs.isEmpty()) {
            return List.of();
        }
        Set<String> codes = favs.stream().map(RptUserFav::getReportCode).collect(Collectors.toSet());
        LambdaQueryWrapper<RptReportDef> defQw = new LambdaQueryWrapper<>();
        defQw.in(RptReportDef::getReportCode, codes);
        defQw.eq(RptReportDef::getStatus, "ACTIVE");
        return reportDefMapper.selectList(defQw);
    }

    /** 收藏一个报表（幂等：已存在则跳过） */
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(String reportCode) {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (userId == null) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.UNAUTHORIZED, "请先登录");
        }
        LambdaQueryWrapper<RptUserFav> qw = new LambdaQueryWrapper<>();
        qw.eq(RptUserFav::getUserId, userId);
        qw.eq(RptUserFav::getReportCode, reportCode);
        if (userFavMapper.selectCount(qw) > 0) {
            return;
        }
        RptUserFav fav = new RptUserFav();
        fav.setUserId(userId);
        fav.setReportCode(reportCode);
        fav.setSortOrder((int) (System.currentTimeMillis() % 1000));
        userFavMapper.insert(fav);
    }

    /** 取消收藏 */
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(String reportCode) {
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<RptUserFav> qw = new LambdaQueryWrapper<>();
        qw.eq(RptUserFav::getUserId, userId);
        qw.eq(RptUserFav::getReportCode, reportCode);
        userFavMapper.delete(qw);
    }
}
