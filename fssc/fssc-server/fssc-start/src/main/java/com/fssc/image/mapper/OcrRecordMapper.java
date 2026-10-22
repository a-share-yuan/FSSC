package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.OcrRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** OCR 识别结果落库 Mapper */
@Mapper
public interface OcrRecordMapper extends BaseMapper<OcrRecord> {

    /**
     * 分页查询 OCR 记录（带批次号）。
     * 关联 img_batch 获取 batch_no；文件名在 master 库的 sys_file，由调用方回填（跨库 JOIN）。
     * 别名用 `or`：MySQL 保留字，`or.*` 直接 1064（D-F1R-11：OCR 识别记录页打开即 500 的根因）。
     */
    @Select("<script>" +
            "select rec.*, ib.batch_no as batchNo " +
            "from ocr_record rec " +
            "left join img_batch ib on rec.batch_id = ib.id " +
            "<where>" +
            "<if test='batchId != null'>and rec.batch_id = #{batchId}</if> " +
            "<if test='invoiceNo != null and invoiceNo != \"\"'>and rec.invoice_no = #{invoiceNo}</if> " +
            "</where> order by rec.id desc" +
            "</script>")
    com.baomidou.mybatisplus.core.metadata.IPage<com.fssc.image.vo.OcrRecordVO> selectPageWithRelations(
            com.baomidou.mybatisplus.core.metadata.IPage<com.fssc.image.vo.OcrRecordVO> page,
            @Param("batchId") Long batchId,
            @Param("invoiceNo") String invoiceNo);
}
