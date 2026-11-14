-- ================================================================
-- V1.4.94  退货原因字典（enum_return_reason_pur / enum_return_reason_sale）
-- ----------------------------------------------------------------
-- 背景（缺陷，2026-10-24 H2 真跑）：采购/销售退货单的「退货原因」下拉
--       此前写死在 fssc-web/src/views/returns/PurchaseReturn.vue 与 SalesReturn.vue
--       的本地数组里，运营要增删一个原因必须发版；两域各写一份，规则也不统一。
--
-- 改造后：两页面改走 useDict('enum_return_reason_pur'/'_sale')，字典有值即以字典为准，
--       字典缺失/接口不通时逐字回落到本地常量（＝本文件的同一组值），界面零变化。
--
-- 值规则：dict_value 与 dict_label 同为中文原文。原因列（pur_return.return_reason /
--       sale_return.return_reason）历史上存的就是中文串，若这里改用英文码，
--       历史单据的原因在列表里会退回"直接显示原值"，故不转码。
-- 采购/销售分列两套：退货原因的业务语义两域不同（采购侧对着供应商来货质量，
--       销售侧对着客户取消/合同终止），合并成一套会让两个下拉都出现无意义选项。
--
-- 去重：sys_dict_type 用 ON DUPLICATE KEY UPDATE；sys_dict_data 先按类型 DELETE 再 INSERT。
-- 回滚见 V1.4.94_return_reason_dict_rollback.sql。
-- ================================================================

INSERT INTO fssc.sys_dict_type
 (tenant_id,dict_type,dict_name,is_builtin,status,create_by,del_flag,remark) VALUES
 (1,'enum_return_reason_pur','采购退货原因（pur_return.return_reason）',1,'NORMAL',NULL,0,'原写死在 PurchaseReturn.vue，改字典驱动'),
 (1,'enum_return_reason_sale','销售退货原因（sale_return.return_reason）',1,'NORMAL',NULL,0,'原写死在 SalesReturn.vue，改字典驱动')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), remark=VALUES(remark), del_flag=0, status='NORMAL';

DELETE FROM fssc.sys_dict_data WHERE tenant_id=1 AND dict_type IN ('enum_return_reason_pur','enum_return_reason_sale');

INSERT INTO fssc.sys_dict_data
 (tenant_id,dict_type,dict_label,dict_value,css_class,sort,is_default,status,create_by,del_flag,remark) VALUES
 (1,'enum_return_reason_pur','质量不合格','质量不合格',NULL,1,0,'NORMAL',NULL,0,'采购退货：来料检验不合格'),
 (1,'enum_return_reason_pur','破损','破损',NULL,2,0,'NORMAL',NULL,0,'采购退货：运输/仓储破损'),
 (1,'enum_return_reason_pur','错发','错发',NULL,3,0,'NORMAL',NULL,0,'采购退货：供应商发错物料'),
 (1,'enum_return_reason_pur','多发','多发',NULL,4,0,'NORMAL',NULL,0,'采购退货：超订单数量到货'),
 (1,'enum_return_reason_pur','合同取消','合同取消',NULL,5,0,'NORMAL',NULL,0,'采购退货：采购合同取消/终止'),
 (1,'enum_return_reason_pur','其他','其他',NULL,6,0,'NORMAL',NULL,0,'采购退货：其它原因，须在备注写明'),
 (1,'enum_return_reason_sale','质量不合格','质量不合格',NULL,1,0,'NORMAL',NULL,0,'销售退货：客户反馈质量问题'),
 (1,'enum_return_reason_sale','破损','破损',NULL,2,0,'NORMAL',NULL,0,'销售退货：运输/交付破损'),
 (1,'enum_return_reason_sale','错发','错发',NULL,3,0,'NORMAL',NULL,0,'销售退货：我方发错货'),
 (1,'enum_return_reason_sale','客户取消','客户取消',NULL,4,0,'NORMAL',NULL,0,'销售退货：客户取消订单'),
 (1,'enum_return_reason_sale','合同终止','合同终止',NULL,5,0,'NORMAL',NULL,0,'销售退货：销售合同终止'),
 (1,'enum_return_reason_sale','其他','其他',NULL,6,0,'NORMAL',NULL,0,'销售退货：其它原因，须在备注写明');

-- 复点（执行后应各为 6）：
-- SELECT dict_type, COUNT(*) FROM fssc.sys_dict_data
--  WHERE tenant_id=1 AND dict_type IN ('enum_return_reason_pur','enum_return_reason_sale')
--    AND del_flag=0 GROUP BY dict_type;
