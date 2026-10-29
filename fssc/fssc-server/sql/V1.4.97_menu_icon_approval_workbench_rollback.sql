-- V1.4.97 回滚：撤掉审批工作台的菜单图标，回到本窗执行前的 NULL 态
-- 回滚后侧栏该项再次没有图标（缺陷复现），其余读数不受影响；
-- 镜像件为执行前的 sys_menu 整表备份，按业务键（id=3916）回位即可，不依赖自增。
UPDATE sys_menu
   SET icon = NULL
 WHERE id = 3916
   AND menu_name = '审批工作台'
   AND path = '/approval'
   AND menu_type = 'C';

SELECT id, menu_name, path, icon FROM sys_menu WHERE id = 3916;
