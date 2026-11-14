package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfNodeConfig;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUTO 取人 L1 硬约束前置修复 · 判据单元测试。
 *
 * <p>全部用 mock 构造 Flowable 历史轨迹，不连库、不改配置、不写业务数据。
 * 验证目标：派发侧 {@link WfSerialExclusion#blockedUsers} 与 {@code complete()}
 * 两道硬闸的判据完全一致，且「无约束节点零查询」「sysauto 豁免」「剔空不硬派」三条不变量成立。
 */
@DisplayName("AUTO 取人：同一实例办人互斥判定")
class WfSerialExclusionTest {

    private HistoryService historyService;
    private HistoricTaskInstanceQuery query;
    private WfNodeConfigResolver nodeConfigResolver;
    private SysConfigMapper sysConfigMapper;
    private SysUserMapper sysUserMapper;
    private WfSerialExclusion guard;

    private static final String PROC = "PROC-1";
    private static final String MODEL = "GL_MANUAL_VOUCHER";
    private static final String GROUP = "VOUCHER_REVIEW_POST";
    private static final String BIZ = "GL_JOURNAL";

    @BeforeEach
    void setUp() {
        historyService = mock(HistoryService.class);
        query = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        nodeConfigResolver = mock(WfNodeConfigResolver.class);
        sysConfigMapper = mock(SysConfigMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        guard = new WfSerialExclusion(historyService, nodeConfigResolver, sysConfigMapper, sysUserMapper);
        // 缺省：连办闸关闭（与线上 GL_MONTH_CLOSE 作用域下普通链一致）
        configValue("0");
        when(nodeConfigResolver.resolve(anyString(), anyString())).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    private void configValue(String value) {
        SysConfig c = new SysConfig();
        c.setConfigValue(value);
        when(sysConfigMapper.selectOne(any(Wrapper.class))).thenReturn(c);
    }

    private HistoricTaskInstance hist(String assignee, String defKey) {
        HistoricTaskInstance h = mock(HistoricTaskInstance.class);
        when(h.getAssignee()).thenReturn(assignee);
        when(h.getTaskDefinitionKey()).thenReturn(defKey);
        return h;
    }

    private void history(HistoricTaskInstance... rows) {
        when(query.list()).thenReturn(List.of(rows));
    }

    private WfNodeConfig cfg(String group) {
        WfNodeConfig c = new WfNodeConfig();
        c.setSerialExclusiveGroup(group);
        return c;
    }

    // --- 连办总闸配置解析（与 complete() 逐字同一） --------------------------

    @Test
    @DisplayName("配置解析：0/空不拦，1/ALL 全量，白名单按 bizType 匹配")
    void serialDeniedConfigValues() {
        assertFalse(guard.serialDenied(null));
        configValue("0");
        assertFalse(guard.serialDenied(BIZ));
        configValue("");
        assertFalse(guard.serialDenied(BIZ));
        configValue("1");
        assertTrue(guard.serialDenied(BIZ));
        configValue("ALL");
        assertTrue(guard.serialDenied(BIZ));
        configValue("GL_MONTH_CLOSE");
        assertTrue(guard.serialDenied("GL_MONTH_CLOSE"));
        assertFalse(guard.serialDenied(BIZ));
        configValue("GL_MONTH_CLOSE,TR_PAY");
        assertTrue(guard.serialDenied("TR_PAY"));
    }

    @Test
    @DisplayName("配置缺行 fail-closed；读取异常只对显式登记链拦截")
    void serialDeniedFailClosed() {
        when(sysConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertTrue(guard.serialDenied(BIZ));
        when(sysConfigMapper.selectOne(any(Wrapper.class)))
                .thenThrow(new RuntimeException("db jitter"));
        assertTrue(guard.serialDenied("GL_MONTH_CLOSE"));
        assertFalse(guard.serialDenied(BIZ));
    }

    // --- 历史轨迹读取 --------------------------------------------------------

    @Test
    @DisplayName("handledUsers 读 act_hi_taskinst、排除同节点重办、忽略非数字 assignee")
    void handledUsersFromAuthorityHistory() {
        history(hist("3797", "GK_1_R"), hist("3821", "GK_4"), hist("sysauto", "GK_2"));
        assertEquals(Set.of(3797L), guard.handledUsers(PROC, "GK_4"));
        assertEquals(Set.of(3797L, 3821L), guard.handledUsers(PROC, null));
    }

    @Test
    @DisplayName("handledInGroup 只认同互斥组的历史节点")
    void handledInGroupOnlySameGroup() {
        history(hist("3797", "GK_1_R"), hist("3821", "GK_3"));
        when(nodeConfigResolver.resolve(MODEL, "GK_1_R")).thenReturn(cfg(GROUP));
        when(nodeConfigResolver.resolve(MODEL, "GK_3")).thenReturn(cfg(null));
        assertEquals(Set.of(3797L), guard.handledInGroup(MODEL, PROC, "GK_4", GROUP));
    }

    // --- L1 组合判定 ---------------------------------------------------------

    @Test
    @DisplayName("无约束节点零成本：不查历史表、返回空集")
    void noConstraintSkipsHistory() {
        when(nodeConfigResolver.resolve(MODEL, "GK_3")).thenReturn(cfg(null));
        assertTrue(guard.blockedUsers(MODEL, BIZ, PROC, "GK_3").isEmpty());
        verify(historyService, never()).createHistoricTaskInstanceQuery();
    }

    @Test
    @DisplayName("互斥组命中：GK_1_R 办理人被剔出 GK_4 候选")
    void groupHitBlocksPreviousHandler() {
        history(hist("3797", "GK_1_R"));
        when(nodeConfigResolver.resolve(MODEL, "GK_4")).thenReturn(cfg(GROUP));
        when(nodeConfigResolver.resolve(MODEL, "GK_1_R")).thenReturn(cfg(GROUP));
        assertEquals(Set.of(3797L), guard.blockedUsers(MODEL, BIZ, PROC, "GK_4"));
    }

    @Test
    @DisplayName("连办总闸生效链：本实例所有已办人都被剔除")
    void serialGateBlocksAllPreviousHandlers() {
        configValue("1");
        history(hist("3797", "GK_1_R"), hist("3821", "GK_3"));
        assertEquals(Set.of(3797L, 3821L), guard.blockedUsers(MODEL, BIZ, PROC, "GK_4"));
    }

    @Test
    @DisplayName("sysauto 豁免：机器审核身份不被剔除")
    void sysautoExempt() {
        configValue("1");
        history(hist("9999", "GK_1_R"), hist("3797", "GK_3"));
        SysUser auto = new SysUser();
        auto.setUserName("sysauto");
        when(sysUserMapper.selectById(9999L)).thenReturn(auto);
        assertEquals(Set.of(3797L), guard.blockedUsers(MODEL, BIZ, PROC, "GK_4"));
    }

    @Test
    @DisplayName("同节点重办放行：历史里就是当前 defKey 的人不算冲突")
    void sameNodeReworkAllowed() {
        configValue("1");
        history(hist("3797", "GK_4"));
        assertTrue(guard.blockedUsers(MODEL, BIZ, PROC, "GK_4").isEmpty());
    }

    @Test
    @DisplayName("hasHandledInInstance 用 count>0，查询异常按未办过处理")
    void hasHandledInInstance() {
        when(query.count()).thenReturn(1L);
        assertTrue(guard.hasHandledInInstance(PROC, 3797L));
        when(query.count()).thenThrow(new RuntimeException("db jitter"));
        assertFalse(guard.hasHandledInInstance(PROC, 3797L));
        assertFalse(guard.hasHandledInInstance(null, 3797L));
    }
}
