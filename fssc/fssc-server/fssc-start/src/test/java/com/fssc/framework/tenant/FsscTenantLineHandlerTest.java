package com.fssc.framework.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FsscTenantLineHandler 单测：验证忽略表白名单与全局忽略逻辑。
 * 白名单：pf_* / sys_menu / sys_dict_type_def / sys_dict_data_def / ACT_* / FLW_*；
 * 其余租户业务表（sys_user、wf_*、exp_* 等）必须被过滤。
 */
class FsscTenantLineHandlerTest {

    private FsscTenantLineHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FsscTenantLineHandler();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void 平台表不参与改写() {
        assertTrue(handler.ignoreTable("pf_tenant"));
        assertTrue(handler.ignoreTable("pf_order"));
        assertTrue(handler.ignoreTable("pf_oper_log"));
    }

    @Test
    void 全局共享定义表不参与改写() {
        assertTrue(handler.ignoreTable("sys_menu"));
        assertTrue(handler.ignoreTable("sys_dict_type_def"));
        assertTrue(handler.ignoreTable("sys_dict_data_def"));
    }

    @Test
    void 流程引擎表不参与改写() {
        assertTrue(handler.ignoreTable("ACT_RU_TASK"));
        assertTrue(handler.ignoreTable("ACT_HI_PROCINST"));
        assertTrue(handler.ignoreTable("FLW_EVENT_DEFINITION"));
        // 大小写不敏感
        assertTrue(handler.ignoreTable("act_ge_property"));
    }

    @Test
    void 租户业务表必须参与改写() {
        assertFalse(handler.ignoreTable("sys_user"));
        assertFalse(handler.ignoreTable("sys_role"));
        assertFalse(handler.ignoreTable("exp_bill"));
        assertFalse(handler.ignoreTable("wf_task"));
        assertFalse(handler.ignoreTable("md_supplier"));
        assertFalse(handler.ignoreTable("op_work_order"));
    }

    @Test
    void 全局忽略模式下所有表都不参与改写() {
        TenantContext.runIgnoreTenant(() -> {
            assertTrue(handler.ignoreTable("sys_user"));
            assertTrue(handler.ignoreTable("exp_bill"));
        });
    }

    @Test
    void 租户上下文下业务表不忽略() {
        TenantContext.set(7L);
        assertFalse(handler.ignoreTable("exp_bill"));
    }
}
