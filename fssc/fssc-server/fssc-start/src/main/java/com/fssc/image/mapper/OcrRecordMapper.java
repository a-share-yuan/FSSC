package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fssc.image.entity.OcrRecord;
import com.fssc.image.vo.OcrRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** OCR 识别结果写入数据库 Mapper */
@Mapper
public interface OcrRecordMapper extends BaseMapper<OcrRecord> {

    /**
     * 分页查询 OCR 记录（带批次号）。
     * 关联 img_batch 获取 batch_no；文件名在 master 库的 sys_file，由调用方回填（不要跨库 JOIN）。
     * 别名不要用 `or`：MySQL 保留字，`or.*` 直接 1064（OCR 识别记录页打开即 500 的根因）。
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
    IPage<OcrRecordVO> selectPageWithRelations(
            IPage<OcrRecordVO> page,
            @Param("batchId") Long batchId,
            @Param("invoiceNo") String invoiceNo);

    /**
     * OCR 记录总数（与 {@link #selectPageWithRelations} 完全相同的筛选条件）。
     *
     * <p>2026-11-14 压测整改：分页插件自动生成的 {@code COUNT(*)} 在 40 万行
     * {@code ocr_record} 上平均 116ms，且每次翻页都重算。改由调用方
     * {@code Page.setSearchCount(false)} + {@code ImagePageKit} 60 秒快照，
     * 本方法作为快照的 loader，每签名 60 秒只跑一次。</p>
     *
     * <p>不带 {@code LEFT JOIN img_batch}：计数条件只落在 rec 上，join 不影响行数，
     * 与分页插件 optimizeJoin 后的口径一致。</p>
     */
    @Select("<script>" +
            "select count(*) from ocr_record rec " +
            "<where>" +
            "<if test='batchId != null'>and rec.batch_id = #{batchId}</if> " +
            "<if test='invoiceNo != null and invoiceNo != \"\"'>and rec.invoice_no = #{invoiceNo}</if> " +
            "</where>" +
            "</script>")
    Long countWithRelations(@Param("batchId") Long batchId, @Param("invoiceNo") String invoiceNo);
}
