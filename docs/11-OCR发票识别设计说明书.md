# 11-OCR 发票识别设计说明书（P9）

> 阶段：P9 OCR 发票识别 
> 覆盖：可插拔 OCR 引擎、批次识别链路、发票自动入库（价税分离）
> 位置：`com.fssc.image.ocr` + `com.fssc.image`（OcrService）

---

## 1. 本阶段交付

### 1.1 可插拔 OCR 架构

```
业务层（OcrService） 只依赖 OcrProvider 接口
  ├── MockOcrProvider（@Primary，演示：确定性数据）
  └── 生产实现（腾讯云/百度 OCR，替换 @Primary 即可，识别流程零改动）
```

- `OcrProvider.recognize(image, fileId) → OcrResult`（票号/代码/类型/卖方/日期/价税分离金额）
- `OcrResult`：发票字段契约（invoiceNo/invoiceCode/netAmount/taxAmount/amount…）

### 1.2 识别链路

```
上传批次（img_batch UPLOADED，页面 img_file ocr_status=NONE）
  → POST /api/tenant/image/batch/{id}/ocr
  → 逐页：NONE → PENDING → 引擎识别
      ├─ 成功：ocr_status=SUCCESS + verify_status=PASS + ocr_text(JSON 回填)
      │        → 生成 inv_invoice（价税分离，自动入库）
      └─ 失败：ocr_status=FAILED（不中断整批）
  → 批次置 CHECKED
```

接口：`POST /api/tenant/image/batch/{id}/ocr`，权限码 `image:ocr:run`，写操作落审计。

## 2. 验证报告（全部实测通过，JDK 26 + MySQL 8）

| 用例 | 结果 |
|---|---|
| 上传批次（2 页） |  |
| 触发 OCR |  生成 2 张发票 |
| 页面状态流转 |  ocr_status=SUCCESS + verify_status=PASS |
| ocr_text 回填 |  JSON 含票号（MOCK00000501）与价税分离字段 |
| 发票自动入库 |  MOCK 发票 2 张 |
| 价税一致 |  5010 + 651.30 = 5661.30 |
| 批次 CHECKED |  |
| 全量冒烟回归 |  verify-demo.sh 全绿 |

## 3. 设计要点

1. **引擎可插拔**：识别契约 OcrProvider 接口 + @Primary 默认 Mock；换真实 OCR 只改实现类，
   识别流程（状态机/回填/发票入库）完全复用。
2. **单页失败不中断整批**：页级 try-catch，FAILED 标记后继续；识别结果 JSON 回填 ocr_text 留痕。
3. **发票自动价税分离入库**：与手工录入共用 inv_invoice 模型（唯一键 uk_tenant_inv 防重）。

## 4. 下一步建议

- PAY 付款单 + 出纳（op_pay_batch 批量付款）
- 真实 OCR 引擎接入（腾讯云 OCR SDK，OcrProvider 替换）
- 报表看板（报销统计/预算执行率）
- H5/PC 前端（报销填单/审批/运营台）


---

## 需求对齐

- **需求基线**：《财务共享平台-AI开发需求文档》（`docs/财务共享平台-AI开发需求文档.md`，来源 `fssc/需求/`）
- **对应章节**：第7章 发票管理（验真/补录/接口）
- **对齐矩阵**：`docs/00-需求与设计索引.md`（11 个一级域 ↔ 设计文档 ↔ 实现状态）

> 注：本文档为落地实现参考；业务功能、字段、页面规则以需求基线为唯一验收标准。如有出入，以需求文档为准。
