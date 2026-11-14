-- =============================================================================
-- V1.4.77  八类差距收尾⑧：外部接口通道配置纠错 + 待接入原因标注
-- 日期：2026-10-17
-- 背景：FSSC未实现项归类复核_8类框架_20261014.md §4-⑧「ext_interface_config 转真」
--
-- 【如实结论：11 条 DISABLED 无法在本轮「转真」，且强行启用更糟】
-- 逐条实测 api_url 后，11 条禁用通道里 10 条缺少真实外部接入信息：
--   · 占位域名 example.com：id=10 Test OCR、id=11 LinkU、id=17 银企直联(TEMPLATE)
--   · api_url 为空：id=12 银企直联(HTTP)、id=13 短信阿里云、id=14 短信腾讯云、
--                   id=15 纳税申报推送、id=16 进项发票认证
--   · mock 假通道：id=1 DEFAULT_OCR（extra_config={"channel":"mock"}）
--   · 配置错误（自指）：id=2 发票验真 —— 见下
-- 而 ExtInterfaceConfigService.getEnabledByCategory 只返回 status=ENABLED 的通道，
-- 全部禁用时返回 null（调用方有降级）。若强行把这些改成 ENABLED，业务会真的去请求
-- example.com / 空地址，反而从「优雅降级」变成「运行期报错」。故本件不做盲目转真。
--
-- 本件做两件确定正确的事：
--   1) 修正 id=2 的脏配置：api_url 曾被填成 http://localhost:8089/api/tenant/system/ext-interface/2
--      ——即把本服务自己的管理端点当成「发票验真外部接口」。GET 它必然返回本服务鉴权响应，
--      旧版 testConnection 只看 HTTP 200 ⇒ 误报「已连通」，掩盖了配置错误。置空并注明。
--   2) 在 remark 里写明每条禁用通道的真实待接入条件，便于运营判断「禁用是待接入」而非故障。
--
-- 代码侧配套修复见 ExtInterfaceConfigService.testConnection（业务错误识别 + 自指拦截）。
-- 回滚：见同名 _rollback.sql
-- =============================================================================

-- 1) 纠正 id=2 自指地址，置空并注明（不编造假地址）
UPDATE ext_interface_config
SET api_url = NULL,
    remark = '待接入：需客户提供真实发票验真服务地址与凭据。原配置误填为本服务管理端点'
             '（http://localhost:8089/api/tenant/system/ext-interface/2），'
             'GET 自身必然返回鉴权响应，会被误判为已连通，故置空。',
    update_time = NOW()
WHERE id = 2
  AND api_url LIKE '%localhost:8089%';

-- 2) 待接入原因标注（只改 remark，不动 status —— 保持「未接入就不启用」的安全默认）
UPDATE ext_interface_config SET remark = '待接入：占位地址 example.com，需替换为真实 OCR 服务端点', update_time = NOW()
WHERE id = 10 AND api_url LIKE '%example.com%';

UPDATE ext_interface_config SET remark = '待接入：占位地址 linku.example.com，需替换为真实 LinkU 平台地址', update_time = NOW()
WHERE id = 11 AND api_url LIKE '%example.com%';

UPDATE ext_interface_config SET remark = '待接入：占位地址 bank.example.com，需替换为真实银行直联地址', update_time = NOW()
WHERE id = 17 AND api_url LIKE '%example.com%';

UPDATE ext_interface_config SET remark = '待接入：通用 HTTP 银企直联，需填入银行网关地址与鉴权凭据', update_time = NOW()
WHERE id = 12 AND (api_url IS NULL OR api_url = '');

UPDATE ext_interface_config SET remark = '待接入：需填入阿里云短信 AccessKey/Secret 与签名', update_time = NOW()
WHERE id = 13 AND (api_url IS NULL OR api_url = '');

UPDATE ext_interface_config SET remark = '待接入：需填入腾讯云短信 SecretId/SecretKey 与签名', update_time = NOW()
WHERE id = 14 AND (api_url IS NULL OR api_url = '');

UPDATE ext_interface_config SET remark = '待接入：需对接电子税务局申报推送地址与证书', update_time = NOW()
WHERE id = 15 AND (api_url IS NULL OR api_url = '');

UPDATE ext_interface_config SET remark = '待接入：需对接勾选/撤销认证平台地址与凭据', update_time = NOW()
WHERE id = 16 AND (api_url IS NULL OR api_url = '');

-- 3) mock 通道如实标注（它当前是 OCR 分类的 is_default=1，
--    但同分类另有已启用的 Baidu OCR id=9，故 getEnabledByCategory 取到的不是它）
UPDATE ext_interface_config
SET remark = 'mock 桩通道：不接真实 OCR，仅供本地联调。OCR 分类默认启用通道为 Baidu OCR(id=9)'
WHERE id = 1 AND extra_config LIKE '%mock%';
