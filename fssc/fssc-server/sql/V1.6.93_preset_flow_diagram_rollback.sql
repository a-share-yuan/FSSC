-- 回退：还原补齐布局前的模型 XML。仅在模型未再次编辑时适用。
UPDATE wf_model SET bpmn_xml = '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_CLAIM_BASIC" targetNamespace="http://fssc">
  <bpmn:process id="CLAIM_BASIC" name="费用报销（两级审批）" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="提交报销单" />
    <bpmn:sequenceFlow id="Flow_start_Task_1" sourceRef="StartEvent_1" targetRef="Task_1" />
    <bpmn:userTask id="Task_1" name="部门经理审批" flowable:candidateGroups="DEPT_MANAGER" />
    <bpmn:sequenceFlow id="Flow_Task_1_Task_2" sourceRef="Task_1" targetRef="Task_2" />
    <bpmn:userTask id="Task_2" name="财务审核" flowable:candidateGroups="FIN_AUDIT" />
    <bpmn:sequenceFlow id="Flow_Task_2_end" sourceRef="Task_2" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1" name="审批完成" />
  </bpmn:process>
</bpmn:definitions>
' WHERE tenant_id=1 AND model_key='CLAIM_BASIC' AND del_flag=0 AND bpmn_xml LIKE '%BPMNDiagram%';
UPDATE wf_model SET bpmn_xml = '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_CLAIM_STD" targetNamespace="http://fssc">
  <bpmn:process id="CLAIM_STD" name="费用报销（三级审批）" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="提交报销单" />
    <bpmn:sequenceFlow id="Flow_start_Task_1" sourceRef="StartEvent_1" targetRef="Task_1" />
    <bpmn:userTask id="Task_1" name="部门经理审批" flowable:candidateGroups="DEPT_MANAGER" />
    <bpmn:sequenceFlow id="Flow_Task_1_Task_2" sourceRef="Task_1" targetRef="Task_2" />
    <bpmn:userTask id="Task_2" name="财务审核" flowable:candidateGroups="FIN_AUDIT" />
    <bpmn:sequenceFlow id="Flow_Task_2_Task_3" sourceRef="Task_2" targetRef="Task_3" />
    <bpmn:userTask id="Task_3" name="财务总监审批" flowable:candidateGroups="CFO" />
    <bpmn:sequenceFlow id="Flow_Task_3_end" sourceRef="Task_3" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1" name="审批完成" />
  </bpmn:process>
</bpmn:definitions>
' WHERE tenant_id=1 AND model_key='CLAIM_STD' AND del_flag=0 AND bpmn_xml LIKE '%BPMNDiagram%';
