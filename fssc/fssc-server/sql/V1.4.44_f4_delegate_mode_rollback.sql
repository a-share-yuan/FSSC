-- ============================================================================
-- V1.4.44 F4 委托语义 —— 回滚脚本（全部注释态，与 seed 成对保存，未执行）
-- 前提：seed 未执行时本文件无需执行；开启后要退路时按下面两条路径任选。
--
-- 演练期 MySQL SELECT-only：本文件同样【一行都没有执行】。
-- ============================================================================

-- ---------- 路径 A：只熄火（推荐，秒级、无数据改写） ----------
-- 把灰度清单清空 ⇒ delegateReassignActive() 返回 false，改派/历史改派/到期回跳全部停发新增加派，
-- 行为回到 BORROW（借资格）＝改造前规则。代码不需要回退。
-- UPDATE sys_config SET config_value='', update_time=NOW() WHERE tenant_id=1 AND config_key='wf.delegate.mode.models';
-- 或整档切回：
-- UPDATE sys_config SET config_value='BORROW', update_time=NOW() WHERE tenant_id=1 AND config_key='wf.delegate.mode';

-- ---------- 路径 B：撤掉配置行（彻底回到"库里没有这两个键"的初始态） ----------
-- 软删（与全库规则一致，硬删会让审计查不到曾经开过校验）：
-- UPDATE sys_config SET del_flag=1, update_time=NOW()
--  WHERE tenant_id=1 AND config_key IN ('wf.delegate.mode','wf.delegate.mode.models');

-- ---------- 熄火后仍留在库里的东西（务必知情，不要以为"关了就没发生"） ----------
-- 1) wf_task.assignee_id：改派期内被换到受托人名下、且此后**已被办理**的任务，办理人不会回跳
--    （已办任务不在 taskAssignee(from).active() 集合里；把已办记录改成另一个人办，是伪造记录）。
--    未办的在委托取消/到期时由 revertDelegatedTasks 自动回到委托人名下。
-- 2) wf_task_history：每次改派一条 DELEGATE_TAKEOVER、每次回跳一条 DELEGATE_REVERT，
--    都是**新增**行（审计表只增不改），熄火后仍是既有事实，保持原样。
-- 3) sys_message：DELEGATE_NEW / DELEGATE_TAKEOVER 站内信为新增行，撤回只会破坏消息表的可追溯性，不回滚。
-- ⇒ 若要把"已改派但未办"的任务手工退回委托人，走 UI 的转办/改派通道（服务端会补记录），
--   不要直接 UPDATE wf_task —— 引擎侧 ACT_RU_TASK 与镜像表会双账不一致（本轮体检反复出现的 D-双账问题）。

-- ---------- 核对语句（回滚后自查，只读） ----------
-- SELECT config_key, config_value FROM sys_config
--  WHERE tenant_id=1 AND config_key LIKE 'wf.delegate%' AND del_flag=0;
-- SELECT action, COUNT(*) FROM wf_task_history WHERE action LIKE 'DELEGATE_%' GROUP BY action;
-- SELECT id, from_user_id, to_user_id, status, begin_time, end_time FROM wf_delegate
--  WHERE del_flag=0 AND status='NORMAL' AND begin_time<=NOW() AND end_time>=NOW();
