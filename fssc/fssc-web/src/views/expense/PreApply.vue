<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <BillWorkbench ref="wb" :bill-type="'APPLY'" :sub-type="subType" :travel="isTravel" :before-submit="onBeforeSubmit">
    <template #create="b">
      <!-- ============ 单据概要 ============ -->
      <el-card header="单据概要">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item label="单据编号">
                <el-input :model-value="b.form.bill.billNo || '（保存后自动生成）'" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单时间">
                <el-input :model-value="b.nowTs()" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单人">
                <el-input :model-value="currentUserName" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item label="申请人" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" show-dept @change="(id) => onApplicantChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="申请人部门">
                <el-input :model-value="applicantDept" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="申请人公司">
                <el-input :model-value="applicantCompany" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item label="币种" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
                  <el-option label="人民币 CNY" value="CNY" />
                  <el-option label="美元 USD" value="USD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="总金额">
                <span style="color: #f59a23; font-weight: 700; font-size: 15px">{{ b.fmt(totalAmount(b)) }} 元</span>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item label="预算费用年度" prop="periodYear">
                <el-select v-model="b.form.bill.periodYear" style="width: 100%">
                  <el-option v-for="y in years" :key="y" :label="y + ' 年'" :value="y" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="标题" prop="title">
                <el-input v-model="b.form.bill.title" :placeholder="subTypeLabel + '标题'" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 出差申请特有：是否补申请 / 出差类型 / 出差事由 -->
          <el-row v-if="isTravel" :gutter="12">
            <el-col :xs="24" :md="8">
<el-form-item label="是否补申请" prop="extJson.supplementApply">
                <el-select v-model="b.form.bill.extJson.supplementApply" style="width: 100%">
                  <el-option label="否" :value="false" />
                  <el-option label="是（不占用预算）" :value="true" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="出差类型" prop="extJson.travelType">
                <el-select v-model="b.form.bill.extJson.travelType" style="width: 100%">
                  <el-option label="国内出差" value="DOMESTIC" />
                  <el-option label="国外出差" value="ABROAD" />
                  <el-option label="三地出差" value="TRIAREA" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="出差事由" prop="extJson.travelReason">
                <el-input v-model="b.form.bill.extJson.travelReason" />
              </el-form-item>
            </el-col>
          </el-row>

<!-- 单据说明（培训必填） -->
          <el-form-item label="单据说明" :required="isTrain" prop="remark">
            <el-input v-model="b.form.bill.remark" type="textarea" :rows="2" :placeholder="isTrain ? '必填' : '选填'" />
          </el-form-item>

          <!-- 超标内容 / 超标说明 -->
          <el-form-item label="超标内容">
            <el-input :model-value="overContent(b)" disabled />
          </el-form-item>
<el-form-item v-if="hasOverStd(b)" label="超标说明" prop="extJson.overReason">
            <el-input v-model="b.form.bill.extJson.overReason" placeholder="超标准时必填" />
          </el-form-item>
          <el-alert type="info" :closable="false" title="提示：费用明细超出差旅标准后，将自动生成“超标内容”，并需填写“超标说明”" />
        </el-form>
      </el-card>

      <!-- ============ 会议申请：会议信息 ============ -->
      <el-card v-if="isMeeting" header="会议信息（费用详情）" style="margin-top: 12px">
<el-form ref="meetingFormRef" :model="b.form.bill.extJson" :rules="meetingRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item label="会议名称" prop="meetingName"><el-input v-model="b.form.bill.extJson.meetingName" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="会议地点" prop="meetingPlace"><el-input v-model="b.form.bill.extJson.meetingPlace" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="开始日期" prop="startDate"><el-date-picker v-model="b.form.bill.extJson.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="b.form.bill.extJson.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="会议主题" prop="topic"><el-input v-model="b.form.bill.extJson.topic" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="参与人数" prop="attendees"><el-input-number v-model="b.form.bill.extJson.attendees" :min="1" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="金额" prop="meetingAmount"><el-input-number v-model="b.form.bill.extJson.meetingAmount" :min="0" :precision="2" style="width: 100%" @change="syncMeetingLine(b)" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="备注" prop="note"><el-input v-model="b.form.bill.extJson.note" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 出差申请：行程信息（费用详情） ============ -->
      <el-card v-if="isTravel" header="行程信息（费用详情）" style="margin-top: 12px">
        <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center">
          <el-button size="small" type="primary" :loading="calcLoading" @click="autoCalcTravel(b)">按差旅标准自动计算住宿/餐补</el-button>
          <span style="color:#909399;font-size:12px">按 城市等级×出差天数×标准金额 自动带出</span>
        </div>
        <el-table :data="b.form.travels" size="small" border empty-text="暂无数据" @row-dblclick="openRowDetail">
          <el-table-column label="出差人员" width="110"><template #header><span style="color:#f56c6c">*</span>出差人员</template>
            <template #default="{ row, $index }"><el-select v-model="row.travelerName" filterable style="width: 100%" placeholder="选择出差人员"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" :disabled="travelDup(b, $index, u.nickName || u.userName)" /></el-select></template>
          </el-table-column>
          <el-table-column label="出发地" width="110"><template #header><span style="color:#f56c6c">*</span>出发地</template>
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column label="目的地" width="110"><template #header><span style="color:#f56c6c">*</span>目的地</template>
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column label="出发日期" width="120"><template #header><span style="color:#f56c6c">*</span>出发日期</template>
            <template #default="{ row }"><el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="结束日期" width="120"><template #header><span style="color:#f56c6c">*</span>结束日期</template>
            <template #default="{ row }"><el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="交通工具" width="100"><template #header><span style="color:#f56c6c">*</span>交通工具</template>
            <template #default="{ row }">
              <el-select v-model="row.trafficType" style="width: 100%">
                <el-option label="飞机" value="AIR" /><el-option label="高铁" value="RAIL" />
                <el-option label="火车" value="TRAIN" /><el-option label="汽车" value="CAR" /><el-option label="轮船" value="SHIP" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="座位等级" width="100"><template #header><span style="color:#f56c6c">*</span>座位等级</template>
            <template #default="{ row }">
              <el-select v-model="row.trafficClass" style="width: 100%">
                <el-option label="经济舱" value="ECONOMY" /><el-option label="商务舱" value="BUSINESS" /><el-option label="头等舱" value="FIRST" />
                <el-option label="二等座" value="SECOND" /><el-option label="一等座" value="FIRST_SEAT" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="是否往返" width="90"><template #header><span style="color:#f56c6c">*</span>是否往返</template>
            <template #default="{ row }">
              <el-select v-model="row.roundTrip" style="width: 100%"><el-option label="单程" :value="false" /><el-option label="往返" :value="true" /></el-select>
            </template>
          </el-table-column>
          <el-table-column label="长途交通费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.trafficAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="住宿费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="餐饮补贴" width="100">
            <template #default="{ row }"><el-input-number v-model="row.allowanceAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="市内交通费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.localTrafficAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="其他费用" width="100">
            <template #default="{ row }"><el-input-number v-model="row.otherAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column label="住宿天数" width="80">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column label="小计" width="100">
            <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ b.fmt(travelTotal(row)) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyTravel(b, $index)">复制</el-button>
              <el-button link size="small" type="danger" @click="b.form.travels.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addTravel(b)">+ 添加行程</el-button>
      </el-card>

<!-- 出差申请：借款信息 -->
      <el-card v-if="isTravel" header="借款信息" style="margin-top: 12px">
        <el-form ref="loanFormRef" :model="b.form.bill.extJson" :rules="loanRules(b.form.bill.extJson)" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="借款金额"><el-input-number v-model="b.form.bill.extJson.loanAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户类型"><el-input :model-value="'员工'" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="收款账户名称" prop="accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="收款账号" prop="accountNo"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行" prop="bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="预计还款日期" prop="expectRepayDate"><el-date-picker v-model="b.form.bill.extJson.expectRepayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :xs="24"><el-form-item label="借款说明"><el-input v-model="b.form.bill.extJson.loanRemark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 培训申请：培训信息 ============ -->
      <el-card v-if="isTrain" header="培训信息" style="margin-top: 12px">
        <el-form ref="trainFormRef" :rules="trainRules" :model="b.form.bill.extJson" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="培训类型" prop="trainType"><el-select v-model="b.form.bill.extJson.trainType" style="width:100%"><el-option label="内训" value="INTERNAL" /><el-option label="外训" value="EXTERNAL" /><el-option label="公开课" value="PUBLIC" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="培训名称" prop="trainName"><el-input v-model="b.form.bill.extJson.trainName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="培训地点" prop="trainPlace"><el-input v-model="b.form.bill.extJson.trainPlace" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开始日期" prop="startDate"><el-date-picker v-model="b.form.bill.extJson.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="b.form.bill.extJson.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="培训时长(天)" prop="duration"><el-input-number v-model="b.form.bill.extJson.duration" :min="1" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="参与人数" prop="attendees"><el-input-number v-model="b.form.bill.extJson.attendees" :min="1" style="width:100%" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="合计成本"><span style="color:#f59a23;font-weight:600">{{ b.fmt(trainTotal(b)) }} 元</span></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="人均成本"><span style="color:#f59a23;font-weight:600">{{ b.fmt(trainTotal(b) / (b.form.bill.extJson.attendees || 1)) }} 元</span></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="是否计划内" prop="planFlag"><el-select v-model="b.form.bill.extJson.planFlag" style="width:100%"><el-option label="是" :value="true" /><el-option label="否" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="是否涉及差旅" prop="travelFlag"><el-select v-model="b.form.bill.extJson.travelFlag" style="width:100%"><el-option label="是" :value="true" /><el-option label="否" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="是否国外出差" prop="abroadFlag"><el-select v-model="b.form.bill.extJson.abroadFlag" style="width:100%"><el-option label="是" :value="true" /><el-option label="否" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24"><el-form-item label="备注" prop="note"><el-input v-model="b.form.bill.extJson.note" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 培训人员及费用详情 -->
      <el-card v-if="isTrain" header="培训人员及费用详情" style="margin-top: 12px">
        <el-table :data="persons" size="small" border empty-text="暂无数据" @row-dblclick="openRowDetail">
          <el-table-column label="姓名" width="120"><template #header><span style="color:#f56c6c">*</span>姓名</template>
            <template #default="{ row, $index }"><el-select v-model="row.name" filterable allow-create default-first-option style="width:100%"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" :disabled="persons.some((p, i) => i !== $index && p.name === (u.nickName || u.userName))" /></el-select></template>
          </el-table-column>
          <el-table-column label="开始日期" width="130"><template #header><span style="color:#f56c6c">*</span>开始日期</template>
            <template #default="{ row }"><el-date-picker v-model="row.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" @change="calcPerson(b, row)" /></template>
          </el-table-column>
          <el-table-column label="结束日期" width="130"><template #header><span style="color:#f56c6c">*</span>结束日期</template>
            <template #default="{ row }"><el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" @change="calcPerson(b, row)" /></template>
          </el-table-column>
          <el-table-column label="天数" width="70">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="出发地" width="120"><template #header><span style="color:#f56c6c">*</span>出发地</template>
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width:100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="目的地" width="120"><template #header><span style="color:#f56c6c">*</span>目的地</template>
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width:100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="出行方式" width="100"><template #header><span style="color:#f56c6c">*</span>出行方式</template>
            <template #default="{ row }"><el-select v-model="row.trafficType" style="width:100%"><el-option label="飞机" value="AIR" /><el-option label="高铁" value="RAIL" /><el-option label="火车" value="TRAIN" /><el-option label="汽车" value="CAR" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="舱位等级" width="100"><template #header><span style="color:#f56c6c">*</span>舱位等级</template>
            <template #default="{ row }"><el-select v-model="row.trafficClass" style="width:100%"><el-option label="经济舱" value="ECONOMY" /><el-option label="商务舱" value="BUSINESS" /><el-option label="二等座" value="SECOND" /></el-select></template>
          </el-table-column>
          <el-table-column label="培训费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.trainFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="长途交通" width="100">
            <template #default="{ row }"><el-input-number v-model="row.trafficFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="住宿费" width="100">
            <template #default="{ row }"><el-input-number v-model="row.hotelFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="市内交通" width="100">
            <template #default="{ row }"><el-input-number v-model="row.localTrafficFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" label="餐饮补贴" width="100">
            <template #default="{ row }"><el-input-number v-model="row.allowanceFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="persons.splice($index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="persons.push(newPerson())">+ 添加培训人员</el-button>
        <div style="margin-top: 6px; font-size: 12px; color: #909399">合计成本 = 培训费 +（涉及差旅时）长途交通/住宿/市内交通/餐饮补贴，按人员汇总</div>
      </el-card>

      <!-- ============ 分摊信息 ============ -->
      <!-- D-V4R-42：exp:share:* 只授予财务/共享/审批类角色，普通员工没有 ⇒ 原先这张卡对员工照常渲染、
           两列还挂必填红星，一保存就红字「缺少权限：exp:share:update」且分摊行不落库。
           现按权限渲染：能读才显示，能写才可编辑（与发票补录页签同手法）。 -->
      <el-card v-if="canViewShares" header="分摊信息" style="margin-top: 12px">
        <el-form :model="b.form" label-width="110px" size="default">
          <!-- 单选组恒有默认值，不存在漏填，故不加红星 -->
          <el-form-item label="费用承担类型">
            <el-radio-group v-model="shareType" :disabled="!canEditShares">
              <el-radio-button value="COST_CENTER">成本中心承担</el-radio-button>
              <el-radio-button value="PROJECT">项目承担</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-table :data="b.form.shares" size="small" border empty-text="暂无数据" @row-dblclick="openRowDetail">
          <el-table-column label="行编号" width="70">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column :label="shareType === 'COST_CENTER' ? '成本中心' : '项目'" min-width="120">
            <template #default="{ row }">
              <el-select v-if="shareType === 'COST_CENTER'" v-model="row.costCenterId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-for="c in b.costCenters" :key="c.id" :label="ccLabel(c)" :value="c.id" />
              </el-select>
              <el-select v-else v-model="row.projectId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-for="p in b.projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="预算科目" min-width="120"><template #header><span v-if="canEditShares" style="color:#f56c6c">*</span>预算科目</template>
            <template #default="{ row }">
              <el-select v-model="row.budgetItemId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-if="row.budgetItemId != null && !b.budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                <el-option v-for="bg in b.budgets" :key="bg.id" :label="budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects })" :value="bg.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="分摊金额" width="130"><template #header><span v-if="canEditShares" style="color:#f56c6c">*</span>分摊金额</template>
            <template #default="{ row }">
              <el-input-number v-model="row.shareAmount" :disabled="!canEditShares" :min="0" :precision="2" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="预算信息提示" min-width="140">
            <template #default="{ row }">
              <span v-if="row.budgetItemId" :style="{ color: shareBudget(b, row).over ? '#f56c6c' : '#67c23a' }">
                {{ shareBudget(b, row).over ? '超出可用额度' : '可支 ' + b.fmt(shareBudget(b, row).available) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canEditShares" label="操作" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="b.form.shares.splice($index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
        <el-button v-if="canEditShares" size="small" style="margin-top: 8px" @click="b.form.shares.push(b.newShare())">+ 添加分摊行</el-button>
        <div style="margin-top: 6px">分摊合计：<span style="color: #f59a23; font-weight: 600">{{ b.fmt(shareTotal(b)) }}</span> 元</div>
        <div v-if="!canEditShares" style="margin-top: 6px; font-size: 12px; color: #909399">
          本卡只读：当前账号没有「修改分摊」权限（exp:share:update），分摊由具备该权限的财务/共享岗维护。不影响提交，也不影响预算占用——无写权限时整单按费用类型的默认预算科目落账。
        </div>
      </el-card>
    </template>
  </BillWorkbench>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { billRules } from '@/constants/billRequired'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { shareBudgetToLine } from '@/composables/useBill'
import { hasPerm } from '@/utils/perm'
import { ccLabel, showRowDetail, budgetLabel, budgetUnseenLabel } from '@/utils/format'
import { userApi, masterApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
const { saveFormHistory } = useAutoFill()
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  subType: { type: String, default: 'MEETING' },
})

const wb = ref(null)
const isMeeting = computed(() => props.subType === 'MEETING')
const isTravel = computed(() => props.subType === 'TRAVEL')
const isTrain = computed(() => props.subType === 'TRAIN')
const subTypeLabel = computed(() => ({ MEETING: '会议申请', TRAVEL: '出差申请', TRAIN: '培训申请' })[props.subType] || '事前申请')

// D-V4R-42：分摊表 exp:share:* 的授权名册里只有财务/共享/审批类角色（普通员工不在册）。
// 能读才显示这张卡、能写才可编辑——否则员工只会看到两列必填红星＋一发 4030。
const canEditShares = computed(() => hasPerm('exp:share:update'))
const canViewShares = computed(() => canEditShares.value || hasPerm('exp:share:list'))

// ---------- 表单校验：登记表口径的通用/年度字段 + 各子类型特有字段 ----------
const formRules = computed(() => ({
  ...billRules('APPLY', props.subType),
  remark: isTrain.value
    ? [{ required: true, message: '单据说明为必填（培训申请）' }]
    : [{ max: 2000, message: '最多 2000 个字符', trigger: 'blur' }],
  'extJson.supplementApply': [{ required: true, message: '请选择是否补申请' }],
  'extJson.travelType': [{ required: true, message: '请选择出差类型' }],
  'extJson.travelReason': [{ required: true, message: '请输入出差事由' }],
  'extJson.overReason': [{ required: true, message: '请输入超标说明' }],
}))
const meetingRules = {
  meetingName: [{ required: true, message: '请输入会议名称' }],
  meetingPlace: [{ required: true, message: '请输入会议地点' }],
  startDate: [{ required: true, message: '请选择开始日期' }],
  endDate: [{ required: true, message: '请选择结束日期' }],
  topic: [{ required: true, message: '请输入会议主题' }],
  attendees: [{ required: true, type: 'number', message: '请输入参与人数', trigger: 'change' }],
  meetingAmount: [{ required: true, type: 'number', min: 0.01, message: '请输入会议金额（大于 0）', trigger: 'change' }],
  note: [{ required: true, message: '请输入备注' }],
}
// 借款信息是出差申请的选填区块：未借款时不该拦提交，填了借款金额才要求还款日期与收款账户（防付错）
function loanRules(extJson) {
  const needLoan = Number(extJson?.loanAmount) > 0
  const req = (msg) => needLoan ? commonRules.required(msg) : []
  return {
    expectRepayDate: needLoan ? commonRules.date('有借款金额，请选择预计还款日期') : [],
    accountName: req('有借款金额，请填写收款账户名称'),
    accountNo: req('有借款金额，请填写收款账号（防付错）'),
    bankName: req('有借款金额，请填写开户行'),
  }
}
const formRef = ref(null)
const meetingFormRef = ref(null)
const loanFormRef = ref(null)
// 培训信息：此前只有裸 required（红星装饰，一条规则都不跑），现补规则并接入提交闸门
const trainFormRef = ref(null)
const trainRules = {
  trainType: commonRules.select('请选择培训类型'),
  trainName: commonRules.required('请输入培训名称'),
  trainPlace: commonRules.required('请输入培训地点'),
  startDate: commonRules.date('请选择开始日期'),
  endDate: commonRules.date('请选择结束日期'),
  duration: [{ required: true, type: 'number', message: '请输入培训时长', trigger: 'change' }],
  attendees: [{ required: true, type: 'number', message: '请输入参与人数', trigger: 'change' }],
  planFlag: commonRules.select('请选择是否计划内'),
  travelFlag: commonRules.select('请选择是否涉及差旅'),
  abroadFlag: commonRules.select('请选择是否国外出差'),
  note: commonRules.required('请输入备注'),
}

const users = ref([])
const shareType = ref('COST_CENTER')
const persons = ref([newPerson()])
const calcLoading = ref(false)
const stdCache = ref(null)

// 差旅标准自动计算：按 城市等级×天数×标准金额 带出 住宿费/餐饮补贴
async function autoCalcTravel(b) {
  if (!b.form.travels.length) { ElMessage.warning('请先添加行程'); return }
  calcLoading.value = true
  try {
    if (!stdCache.value) {
      const r = await masterApi.std.page({ pageNum: 1, pageSize: 200 })
      stdCache.value = r.list || []
    }
    let filled = 0
    for (const t of b.form.travels) {
      if (!t.toCityId || !t.days) continue
      const city = b.cities.find((x) => x.id === t.toCityId)
      const lvl = city ? city.cityLevel : 'C'
      const hotel = stdCache.value.find((s) => (s.stdName || '').includes('住宿') && (!s.cityLevel || s.cityLevel === lvl))
      const meal = stdCache.value.find((s) => (s.stdName || '').includes('餐补') && (!s.cityLevel || s.cityLevel === lvl))
      if (hotel) t.hotelAmount = Math.round(Number(t.days) * Number(hotel.amount) * 100) / 100
      if (meal) t.allowanceAmount = Math.round(Number(t.days) * Number(meal.amount) * 100) / 100
      calcTravel(b, t)
      filled++
    }
    ElMessage.success(`已按标准计算 ${filled} 条行程（住宿/餐补）`)
  } catch (e) { ElMessage.error(e.message) } finally { calcLoading.value = false }
}
const currentUserName = ref(store.user?.username || store.user?.userName || '当前用户')

function newPerson() {
  return { name: '', startDate: '', endDate: '', days: 0, fromCityId: null, toCityId: null, trafficType: 'AIR', trafficClass: 'ECONOMY', trainFee: 0, trafficFee: 0, hotelFee: 0, localTrafficFee: 0, allowanceFee: 0 }
}
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]

// ---------- 金额计算 ----------
function travelTotal(row) {
  return Number(row.trafficAmount || 0) + Number(row.hotelAmount || 0) + Number(row.allowanceAmount || 0) + Number(row.localTrafficAmount || 0) + Number(row.otherAmount || 0)
}
function calcTravel(b, row) {
  if (row.beginDate && row.endDate) {
    const d = (new Date(row.endDate) - new Date(row.beginDate)) / 86400000
    row.days = Math.max(0, Math.round(d)) + (row.roundTrip ? 1 : 0)
  }
  row.totalAmount = Math.round(travelTotal(row) * 100) / 100
  syncTravelLine(b)
}
function copyTravel(b, i) {
  const t = b.form.travels[i]
  // 复制行清空日期：同人同日两段行程＝重复登记（D-TR-01），由用户填新段日期
  b.form.travels.push({ ...t, id: null, beginDate: '', endDate: '', days: 0 })
}
// D-TR-01 同类口径：同一人同一天出现两条行程＝重复登记（多段行程日期不同，不受影响）
function travelDup(b, index, name) {
  const cur = b.form.travels[index]
  if (!cur || !name) return false
  return b.form.travels.some((t, i) => i !== index && t.travelerName === name && (t.beginDate || '') === (cur.beginDate || ''))
}
function addTravel(b) {
  b.form.travels.push(b.newTravel())
}

function meetingAmount(b) { return b.form.bill.extJson.meetingAmount || 0 }
function syncMeetingLine(b) {
  const amt = Number(b.form.bill.extJson.meetingAmount || 0)
  ensureLine(b)
  b.form.lines[0].amount = amt
  b.form.lines[0].itemId = meetingItemId(b)
}
function meetingItemId(b) {
  const it = b.items.find((x) => (x.itemName || '').includes('会议'))
  return it ? it.id : (b.items[0] ? b.items[0].id : null)
}
function syncTravelLine(b) {
  ensureLine(b)
  const total = b.form.travels.reduce((a, t) => a + Number(t.totalAmount || 0), 0)
  b.form.lines[0].amount = Math.round(total * 100) / 100
  b.form.lines[0].itemId = travelItemId(b)
}
function travelItemId(b) {
  const it = b.items.find((x) => (x.itemName || '').includes('差旅'))
  return it ? it.id : (b.items[0] ? b.items[0].id : null)
}
function trainTotal(b) {
  return persons.value.reduce((a, p) => a + Number(p.trainFee || 0) + Number(p.trafficFee || 0) + Number(p.hotelFee || 0) + Number(p.localTrafficFee || 0) + Number(p.allowanceFee || 0), 0)
}
function syncTrainLine(b) {
  ensureLine(b)
  b.form.lines[0].amount = Math.round(trainTotal(b) * 100) / 100
  b.form.lines[0].itemId = trainItemId(b)
}
function trainItemId(b) {
  const it = b.items.find((x) => (x.itemName || '').includes('培训'))
  return it ? it.id : (b.items[0] ? b.items[0].id : null)
}
function calcTrain(b) {
  if (b.form.bill.extJson.startDate && b.form.bill.extJson.endDate) {
    b.form.bill.extJson.duration = Math.max(1, Math.round((new Date(b.form.bill.extJson.endDate) - new Date(b.form.bill.extJson.startDate)) / 86400000) + 1)
  }
  syncTrainLine(b)
}
function calcPerson(b, row) {
  if (row.startDate && row.endDate) {
    row.days = Math.max(1, Math.round((new Date(row.endDate) - new Date(row.startDate)) / 86400000) + 1)
  }
  syncTrainLine(b)
}
// 确保存在一条费用明细行（预算占用/提交都依赖 lines）
function ensureLine(b) {
  if (!b.form.lines.length) b.form.lines.push(b.newLine())
  // D-V4R-35：合成行的预算科目取分摊行那枚——服务端占用/核销只读明细行的科目，
  // 分摊行的科目本身没有任何消费方，不写回去就是「屏幕选招待费、系统占差旅费」两套口径。
  shareBudgetToLine(b.form.lines, b.form.shares)
}
// D-TR-02：persons 与 extJson.persons 双向同步——草稿即存（此前只在提交时写，重开草稿人员丢失），
// 编辑重提 loadBillForEdit 回读 extJson 时反向回填本地清单（此前永远停在一行空人员）
watch(persons, () => {
  const b = wb.value && wb.value.b
  if (!b || !isTrain.value) return
  const ext = b.form.bill.extJson
  if (JSON.stringify(ext.persons || []) !== JSON.stringify(persons.value)) ext.persons = persons.value.map((p) => ({ ...p }))
}, { deep: true })
watch(() => {
  const b = wb.value && wb.value.b
  return b && b.form.bill.extJson.persons
}, (v) => {
  if (!isTrain.value || !Array.isArray(v)) return
  if (JSON.stringify(persons.value) !== JSON.stringify(v)) persons.value = v.map((p) => ({ ...newPerson(), ...p }))
}, { deep: true })
// D-V4R-35：分摊行的预算科目一变就同步进承载金额的那条行——保存草稿也要带一致科目
// （服务端提交时是从库里读 exp_bill_line 做占用，草稿阶段没写进去，提交就还是两套口径）
watch(() => {
  const b = wb.value && wb.value.b
  return b ? b.form.shares.map((s) => s.budgetItemId).join(',') : ''
}, () => {
  const b = wb.value && wb.value.b
  if (b) shareBudgetToLine(b.form.lines, b.form.shares)
})
function totalAmount(b) {
  if (isMeeting.value) return Number(b.form.bill.extJson.meetingAmount || 0)
  if (isTravel.value) return b.form.travels.reduce((a, t) => a + Number(t.totalAmount || 0), 0)
  if (isTrain.value) return trainTotal(b)
  return b.sumAmount()
}

// ---------- 提交前同步（把子类型特有内容写入 extJson/明细行） ----------
async function onBeforeSubmit(b) {
  // 表头必填闸门：与红星同源，提示具体漏填项并定位
  if (!(await validateForms([formRef]))) return false
  if (isMeeting.value && !(await validateForms([meetingFormRef]))) return false
  if (isTravel.value) {
    // 出差行程行校验：人员/出发地/目的地/日期/交通/座位必填
    const bad = b.form.travels.find((t) => !t.travelerName || !t.fromCityId || !t.toCityId || !t.beginDate || !t.endDate || !t.trafficType || !t.trafficClass)
    if (bad) { ElMessage.warning('出差行程必填项缺失（出差人员/出发地/目的地/日期/交通工具/座位等级）'); return false }
    // D-TR-01 同类口径：同人同日重复行程提交闸
    const dupT = b.form.travels.find((t, i) => travelDup(b, i, t.travelerName))
    if (dupT) { ElMessage.warning(`出差人员「${dupT.travelerName}」同一天存在重复行程，请删除或改期`); return false }
    if (!(await validateForms([loanFormRef]))) return false
  }
  if (isTrain.value && !(await validateForms([trainFormRef]))) return false
  if (isMeeting.value) syncMeetingLine(b)
  if (isTravel.value) syncTravelLine(b)
  if (isTrain.value) {
    // D-TR-01：姓名必填 + 同名唯一（同名重复会把人员费用直接翻倍进单据金额）
    const named = persons.value.map((p) => (p.name || '').trim()).filter(Boolean)
    if (named.length < persons.value.length) { ElMessage.warning('培训人员姓名不能为空'); return false }
    const dup = named.find((n, i) => named.indexOf(n) !== i)
    if (dup) { ElMessage.warning(`培训人员「${dup}」重复登记，同名人员只允许一行`); return false }
    // RA-06 乙案：人数与人员清单不一致仅二次确认，不硬拦（按人头汇总一行是合法用法）
    const att = Number(b.form.bill.extJson.attendees || 0)
    if (att && att !== persons.value.length) {
      try {
        await ElMessageBox.confirm(`参与人数 ${att} 与培训人员清单 ${persons.value.length} 人不一致，确认按当前人员清单提交？`, '人数不一致', { type: 'warning' })
      } catch { return false }
    }
    syncTrainLine(b)
    b.form.bill.extJson.persons = persons.value.map((p) => ({ ...p }))
  }
  // 分摊：成本中心/项目类型写入每条分摊行
  b.form.shares.forEach((s) => {
    s.shareType = shareType.value
    if (shareType.value === 'PROJECT') s.costCenterId = null
    else s.projectId = null
  })
  // D-V4R-35：一屏一尺。分摊行的科目写进承载金额的那条明细行（服务端占用/核销只认行上的科目）；
  // 多科目分摊或一页多行＝这一条预算线承载不了，当场拒提点名，静默择一科目把钱占进别的预算线。
  // D-V4R-42：只读态（无 exp:share:update）不下这条闸——用户改不动分摊行，拦他＝死路
  if (canEditShares.value && shareBudgetToLine(b.form.lines, b.form.shares).conflict) {
    ElMessage.warning('事前申请整单只落一条预算线：分摊行的预算科目不一致（或多条费用明细行），请把分摊行改成同一科目或拆单后再提交')
    return false
  }
  return true
}

// ---------- 超标 ----------
function overContent(b) {
  if (isTravel.value) {
    const overRows = b.form.travels.filter((t) => t.overStd === 1)
    if (overRows.length) return `共 ${overRows.length} 条行程超出差旅标准，请填写超标说明`
  }
  return b.form.lines.some((l) => l.overStd === 1) ? '费用明细超出报销标准，请填写超标说明' : ''
}
function hasOverStd(b) { return !!overContent(b) }

// ---------- 分摊 ----------
function shareTotal(b) { return b.form.shares.reduce((a, s) => a + Number(s.shareAmount || 0), 0) }
function shareBudget(b, row) {
  const bg = b.budgets.find((x) => String(x.id) === String(row.budgetItemId)) || {}
  const avail = Number(bg.totalAmount || 0) + Number(bg.adjustAmount || 0) - Number(bg.occupyAmount || 0) - Number(bg.usedAmount || 0)
  return { available: avail, over: Number(row.shareAmount || 0) > avail }
}

// ---------- 申请人 ----------
async function loadUsers() {
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
    users.value = list
  } catch (e) { users.value = [] }
}
function onApplicantChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}
const applicantDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})
const applicantCompany = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.companyName || u.company)) || '—'
})

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

const route = useRoute()
const router = useRouter()
onMounted(async () => {
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单（子类型页面壳由路由保证）
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      router.replace({ query: {} })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  await loadUsers()
currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '当前用户'
  // 默认申请人 = 当前登录人
  const b = wb.value && wb.value.b
const uid = store.user?.userId ?? store.user?.id
  if (b && !b.form.bill.applyUserId) {
    // P1-5 默认申请人 = 当前登录人，严格匹配 me.id，不回退 users[0]（users 分页可能含非本人/残留账号）
    const me = uid ? users.value.find((x) => x.id === uid) : null
    if (me) {
      b.form.bill.applyUserId = me.id
      b.form.bill.applyUserName = me.nickName || me.userName
    } else {
      b.form.bill.applyUserId = null
      b.form.bill.applyUserName = ''
      if (uid != null) ElMessage.warning('未在用户列表中匹配到当前登录人，请手动选择申请人')
    }
  }
  if (b && b.form.bill.title === '') b.form.bill.title = subTypeLabel.value
})
</script>
