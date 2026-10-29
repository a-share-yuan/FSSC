// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段脱敏 Jackson 集成测试。
 *
 * <p>覆盖：注解驱动脱敏、全局开关、反序列化不受影响（明文入参可保存）。
 * 本测试即"脱敏器必须真正生效"的回归防线——曾因
 * {@code @JacksonAnnotationsInside} 未生效导致出参不脱敏。</p>
 */
class SensitiveMaskTest {

    /** 测试实体：phone/email 脱敏，remark 明文 */
    public static class Demo {
        private String phone;
        private String email;
        private String remark;

        public Demo() {
        }

        public Demo(String phone, String email, String remark) {
            this.phone = phone;
            this.email = email;
            this.remark = remark;
        }

        @Sensitive(SensitiveType.PHONE)
        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Sensitive(SensitiveType.EMAIL)
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    private ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new SensitiveModule());
        return m;
    }

    @AfterEach
    void restore() {
        SensitiveSerializer.setEnabled(true);
    }

    @Test
    void 出参按注解类型脱敏() throws Exception {
        String json = mapper().writeValueAsString(new Demo("13800138000", "abcdef@qq.com", "原始备注"));
        assertTrue(json.contains("138****8000"), "手机号应脱敏: " + json);
        assertTrue(json.contains("ab***@qq.com"), "邮箱应脱敏: " + json);
        assertTrue(json.contains("原始备注"), "非敏感字段应保持明文: " + json);
        assertFalse(json.contains("13800138000"), "不得泄漏明文手机号");
    }

    @Test
    void 全局开关关闭时返回明文() throws Exception {
        SensitiveSerializer.setEnabled(false);
        String json = mapper().writeValueAsString(new Demo("13800138000", "abcdef@qq.com", "r"));
        assertTrue(json.contains("13800138000"), "开关关闭应返回明文: " + json);
        assertTrue(json.contains("abcdef@qq.com"), "开关关闭应返回明文邮箱: " + json);
    }

    @Test
    void 反序列化不受脱敏影响_明文可入参保存() throws Exception {
        Demo d = mapper().readValue(
                "{\"phone\":\"13800138000\",\"email\":\"abcdef@qq.com\",\"remark\":\"r\"}", Demo.class);
        assertEquals("13800138000", d.getPhone());
        assertEquals("abcdef@qq.com", d.getEmail());
    }

    @Test
    void 已脱敏值二次序列化保持稳定() throws Exception {
        String once = mapper().writeValueAsString(new Demo("13800138000", "abcdef@qq.com", "r"));
        // 模拟脱敏串被再次赋值的场景（如前端回传）
        String twice = mapper().writeValueAsString(new Demo("138****8000", "ab***@qq.com", "r"));
        assertTrue(once.contains("138****8000"));
        assertTrue(twice.contains("138****8000"));
        assertFalse(twice.contains("*****8000"), "不得逐层叠加脱敏");
    }

    @Test
    void 空值与空串安全() throws Exception {
        String json = mapper().writeValueAsString(new Demo(null, "", "r"));
        assertTrue(json.contains("\"phone\":null"), json);
        assertTrue(json.contains("\"email\":\"\""), json);
    }

    @Test
    void 默认开关为开启() {
        assertTrue(SensitiveSerializer.isEnabled());
    }
}
