package com.fssc.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MaskUtil 单测：验证脱敏口径稳定、幂等、边界安全。
 */
class MaskUtilTest {

    @Test
    void phone_保留前3后4() {
        assertEquals("138****8000", MaskUtil.phone("13800138000"));
        assertEquals("138****8000", MaskUtil.phone("138 0013 8000".replace(" ", "")));
    }

    @Test
    void phone_边界() {
        assertNull(MaskUtil.phone(null));
        assertEquals("", MaskUtil.phone(""));
        assertEquals("***", MaskUtil.phone("138"));       // 长度 < 7 全遮
        assertEquals("******", MaskUtil.phone("138001"));  // 长度 6 全遮
    }

    @Test
    void email_本地部分保留前2位() {
        assertEquals("ab***@example.com", MaskUtil.email("abcdefg@example.com"));
        assertEquals("ab***@qq.com", MaskUtil.email("ab1@qq.com"));
    }

    @Test
    void email_边界与无域名() {
        assertNull(MaskUtil.email(null));
        assertEquals("ab***@x.com", MaskUtil.email("abc@x.com"));
        assertEquals("*****", MaskUtil.email("abcde"));     // 无 @ 全遮
        assertEquals("**@x.com", MaskUtil.email("ab@x.com")); // local<=2
    }

    @Test
    void bankAccount_保留前4后4() {
        assertEquals("6222********1234", MaskUtil.bankAccount("6222020202021234"));
        assertEquals("6222********1234", MaskUtil.bankAccount("6222 0202 0202 1234".replace(" ", "")));
    }

    @Test
    void bankAccount_边界() {
        assertNull(MaskUtil.bankAccount(null));
        assertEquals("****", MaskUtil.bankAccount("1234")); // <=8 全遮
        assertEquals("********", MaskUtil.bankAccount("12345678"));
    }

    @Test
    void idCard_保留前6后4() {
        assertEquals("110101********1234", MaskUtil.idCard("110101199001011234"));
    }

    @Test
    void name_姓名脱敏() {
        assertEquals("张", MaskUtil.name("张"));
        assertEquals("张*", MaskUtil.name("张三"));
        assertEquals("张*三", MaskUtil.name("张三三"));
        assertEquals("欧**娜", MaskUtil.name("欧阳娜娜"));
        assertNull(MaskUtil.name(null));
    }

    @Test
    void masked_识别已脱敏串() {
        assertTrue(MaskUtil.masked("138****8000"));
        assertFalse(MaskUtil.masked("13800138000"));
        assertFalse(MaskUtil.masked(null));
        assertFalse(MaskUtil.masked(""));
    }

    @Test
    void 幂等_二次脱敏不改变结果() {
        String once = MaskUtil.phone("13800138000");
        assertEquals(once, MaskUtil.phone(once));
        String mail = MaskUtil.email("abcdef@x.com");
        assertEquals(mail, MaskUtil.email(mail));
    }
}
