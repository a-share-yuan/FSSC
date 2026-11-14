package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 待办任务业务视图（带 tenant_id） */
@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {

    /**
     * 按办理人聚合窗口内办结量/停留/超时。
     *
     * <p>原先 {@code WfPerfService.handlerTop} 把窗口内的 DONE 任务<b>整表读进堆</b>再逐行累加：
     * 30 天窗口在百万级任务规模下要命中十几万行，单发近 400 秒，
     * 看板一打开就超时。这里把计数、分钟和、最长分钟下推到 SQL，只回每人一行。
     *
     * <p><b>超时判定与 {@code WfPerfService.overdueOf()} 逐字同规则</b>：
     * <ul>
     *   <li>到期时刻优先取 {@code due_time}；缺失时按 {@code create_time + 节点 timeout_hours} 回算
     *       （{@code wf_node_config} 按 model_key+node_key 唯一，见 {@code uk_tenant_model_node}）；</li>
     *   <li>两者都没有 ⇒ 不可测，不计超时；</li>
     *   <li>办结时刻取 {@code update_time}，缺失按 now；比较用 {@code TIMESTAMPDIFF(MINUTE)} > 0，
     *       与 Java 的 {@code Duration.toMinutes()} 同截断口径（差 1 秒不算超时）。</li>
     * </ul>
     *
     * <p><b>不要删掉这行索引提示：{@code FORCE INDEX (idx_tenant_done_agg)}</b>。优化器按「GROUP BY assignee_id
     * 可用 idx_tenant_assignee 免排序」判定其更便宜（估算 26,881），实际要顺着该索引扫完全部 157 万条
     * DONE 再逐行回表取 create_time 过滤，要 5 秒出头；同一 SQL 换覆盖索引只扫窗口内的索引项，
     * 0.75 秒。索引列序与 WHERE/GROUP/SELECT 全覆盖
     * （tenant_id,status,create_time,assignee_id,due_time,update_time,model_key,node_key），
     * 回表为零；stats 刷新后优化器仍会翻回错计划，故用索引提示钉死。
     */
    @Select("select t.assignee_id as userId, count(*) as handled, " +
            "sum(greatest(0, timestampdiff(MINUTE, t.create_time, ifnull(t.update_time, now())))) as minutesSum, " +
            "max(greatest(0, timestampdiff(MINUTE, t.create_time, ifnull(t.update_time, now())))) as maxMinutes, " +
            "sum(case when t.due_time is not null " +
            "          and timestampdiff(MINUTE, t.due_time, ifnull(t.update_time, now())) > 0 then 1 " +
            "         when t.due_time is null and nc.timeout_hours is not null and nc.timeout_hours > 0 " +
            "          and timestampdiff(MINUTE, date_add(t.create_time, interval nc.timeout_hours hour), " +
            "                            ifnull(t.update_time, now())) > 0 then 1 " +
            "         else 0 end) as overdueHandled " +
            "from wf_task t force index (idx_tenant_done_agg) left join wf_node_config nc " +
            "  on nc.model_key = t.model_key and nc.node_key = t.node_key " +
            "where t.status = 'DONE' and t.create_time >= #{from} and t.assignee_id is not null " +
            "group by t.assignee_id")
    List<Map<String, Object>> handlerDoneStats(@Param("from") LocalDateTime from);
}
