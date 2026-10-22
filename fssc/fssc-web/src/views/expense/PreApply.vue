<template>
  <BillWorkbench ref="wb" :bill-type="'APPLY'" :sub-type="subType" :travel="isTravel" :before-submit="onBeforeSubmit">
    <template #create="b">
      <!-- ============ 单据概要 ============ -->
      <el-card :header="$t('expense.claim.colBillSummary')">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colBillNoFull')">
                <el-input :model-value="b.form.bill.billNo || $t('expense.common.txtAutoAfterSave')" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colMakeTime')">
                <!-- 编辑草稿时制单时间＝库里那张单的建单时刻；原先恒绑 nowTs()，每次编辑都显示"此刻"，与单据本身不符 -->
                <el-input :model-value="b.form.bill.id ? formatDateTime(b.form.bill.createTime) : b.nowTs()" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colMaker')">
                <el-input :model-value="currentUserName" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item :label="$t('expense.common.colApplicant')" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" show-dept @change="(id) => onApplicantChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colApplicantDept')">
                <el-input :model-value="applicantDept" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colApplicantCompany')">
                <el-input :model-value="applicantCompany" disabled />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item :label="$t('expense.common.colCurrency')" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />

                  
                  
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.common.colTotalAmount')">
                <span style="color: #f59a23; font-weight: 700; font-size: 15px">{{ $t('expense.common.amtYuan', { v1: b.fmt(totalAmount(b)) }) }}</span>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item :label="$t('expense.claim.colBudgetYear')" prop="periodYear">
                <el-select v-model="b.form.bill.periodYear" style="width: 100%">
                  <el-option v-for="y in years" :key="y" :label="$t('expense.claim.optYear', { y })" :value="y" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.common.colTitle')" prop="title">
                <el-input v-model="b.form.bill.title" :placeholder="$t('expense.preapply.phTitleBySubType', { v1: subTypeLabel })" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 出差申请特有：是否补申请 / 出差类型 -->
          <el-row v-if="isTravel" :gutter="12">
            <el-col :xs="24" :md="12">
<el-form-item :label="$t('expense.preapply.lblRetroApply')" prop="extJson.supplementApply">
                <el-select v-model="b.form.bill.extJson.supplementApply" style="width: 100%">
                  <el-option :label="$t('common.no')" :value="false" />
                  <el-option :label="$t('expense.preapply.optYesNoBudget')" :value="true" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.travelClaim.colTripType')" prop="extJson.travelType">
                <el-select v-model="b.form.bill.extJson.travelType" style="width: 100%">
                  <el-option v-for="o in travelTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 出差事由＝审批人真正要读的那一栏（必填、落 ext_json TEXT）⇒ 给它整行多行框；
               单据说明是补充备注（remark varchar(1000)，培训才必填）⇒ 收成单行。 -->
          <el-form-item v-if="isTravel" :label="$t('expense.preapply.lblTripReason')" prop="extJson.travelReason">
            <el-input v-model="b.form.bill.extJson.travelReason" type="textarea" :rows="3" maxlength="300" show-word-limit :placeholder="$t('expense.preapply.phTripReason')" />
          </el-form-item>

<!-- 单据说明（培训必填） -->
          <el-form-item :label="$t('expense.claim.colBillDesc')" :required="isTrain" prop="remark">
            <el-input v-model="b.form.bill.remark" :placeholder="isTrain ? $t('expense.preApply.phRequired') : $t('expense.preApply.phOptional')" />
          </el-form-item>

          <!-- 超标内容 / 超标说明 -->
          <el-form-item :label="$t('expense.preapply.colOverContent')">
            <el-input :model-value="overContent(b)" disabled />
          </el-form-item>
<el-form-item v-if="hasOverStd(b)" :label="$t('expense.preapply.colOverNote')" prop="extJson.overReason">
            <el-input v-model="b.form.bill.extJson.overReason" :placeholder="$t('expense.preapply.phRequiredOver')" />
          </el-form-item>
          <el-alert type="info" :closable="false" :title="$t('expense.preapply.hintOverStdAuto')" />
        </el-form>
      </el-card>

      <!-- ============ 会议申请：会议信息 ============ -->
      <el-card v-if="isMeeting" :header="$t('expense.preapply.secMeetingInfo')" style="margin-top: 12px">
<el-form ref="meetingFormRef" :model="b.form.bill.extJson" :rules="meetingRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.lblMeetingName')" prop="meetingName"><el-input v-model="b.form.bill.extJson.meetingName" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.lblMeetingPlace')" prop="meetingPlace"><el-input v-model="b.form.bill.extJson.meetingPlace" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.colStartDate')" prop="startDate"><el-date-picker v-model="b.form.bill.extJson.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="(d) => dayAfter(d, b.form.bill.extJson.endDate)" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.colEndDate')" prop="endDate"><el-date-picker v-model="b.form.bill.extJson.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="(d) => dayBefore(d, b.form.bill.extJson.startDate)" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.lblMeetingTopic')" prop="topic"><el-input v-model="b.form.bill.extJson.topic" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.preapply.colAttendees')" prop="attendees"><el-input-number v-model="b.form.bill.extJson.attendees" :min="1" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('field.amount')" prop="meetingAmount"><el-input-number v-model="b.form.bill.extJson.meetingAmount" :min="0" :precision="2" style="width: 100%" @change="syncMeetingLine(b)" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('common.remark')" prop="note"><el-input v-model="b.form.bill.extJson.note" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 出差申请：行程信息（费用详情） ============ -->
      <el-card v-if="isTravel" :header="$t('expense.preapply.secTripInfoDetail')" style="margin-top: 12px">
        <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center">
          <el-button size="small" type="primary" :loading="calcLoading" @click="autoCalcTravel(b)">{{ $t('expense.preapply.btnAutoByPolicy') }}</el-button>
          <span style="color:#909399;font-size:12px">{{ $t('expense.preapply.hintCityTierAuto') }}</span>
        </div>
        <el-table :data="b.form.travels" size="small" border :empty-text="$t('common.empty')" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.preapply.lblTraveler')" width="110"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.lblTraveler') }}</template>
            <template #default="{ row, $index }"><el-select v-model="row.travelerName" filterable style="width: 100%" :placeholder="$t('expense.common.phPickTraveler')" @change="onTravelerPick(row)"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" :disabled="travelDup(b, $index, u.nickName || u.userName)" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colDeparture')" width="110"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.travelClaim.colDeparture') }}</template>
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width: 100%"><el-option v-for="c in travelCities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colDestination')" width="110"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.travelClaim.colDestination') }}</template>
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width: 100%"><el-option v-for="c in travelCities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colDepartDate')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colDepartDate') }}</template>
            <template #default="{ row }"><el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="(d) => dayAfter(d, row.endDate)" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colEndDate')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colEndDate') }}</template>
            <template #default="{ row }"><el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="(d) => dayBefore(d, row.beginDate)" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colTrafficType')" width="100"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colTrafficType') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.trafficType" style="width: 100%" @change="onTrafficTypeChange(row)">
                <el-option v-for="o in trafficOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.lblSeatClass')" width="100"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.lblSeatClass') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.trafficClass" style="width: 100%">
                <el-option v-for="o in seatOptionsOf(row.trafficType)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.lblRoundTrip')" width="90"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.lblRoundTrip') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.roundTrip" style="width: 100%" @change="calcTravel(b, row)"><el-option :label="$t('expense.preapply.optOneWay')" :value="false" /><el-option :label="$t('expense.preapply.optRoundTrip')" :value="true" /></el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colLongTrafficFee')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.trafficAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colHotel')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colMealAllowance')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.allowanceAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colLocalTrafficFee')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.localTrafficAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.tabOtherFee')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.otherAmount" :min="0" :precision="2" style="width: 100%" @change="calcTravel(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.lblHotelNights')" width="80">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colSubtotal')" width="110">
            <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ b.fmt(travelTotal(row)) }}</span></template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="110" fixed="right">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyTravel(b, $index)">{{ $t('expense.common.btnCopy') }}</el-button>
              <el-button link size="small" type="danger" @click="removeTravel(b, $index)">{{ $t('expense.preapply.btnDelete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addTravel(b)">{{ $t('expense.preapply.btnAddTrip') }}</el-button>
      </el-card>

<!-- 出差申请：借款信息 -->
      <el-card v-if="isTravel" :header="$t('expense.preapply.secLoanInfo')" style="margin-top: 12px">
        <el-form ref="loanFormRef" :model="b.form.bill.extJson" :rules="loanRules(b.form.bill.extJson)" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.loan.colLoanAmount')"><el-input-number v-model="b.form.bill.extJson.loanAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.travelClaim.colAccountType')"><el-input :model-value="'员工'" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colPayeeAccountName')" prop="accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colPayeeAccount')" prop="accountNo"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colBank')" prop="bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.loan.colExpectedRepayDate')" prop="expectRepayDate"><el-date-picker v-model="b.form.bill.extJson.expectRepayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="(d) => dayBefore(d, b.form.bill.applyDate || b.today())" /></el-form-item></el-col>
            <el-col :xs="24"><el-form-item :label="$t('expense.loan.colLoanRemark')"><el-input v-model="b.form.bill.extJson.loanRemark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 培训申请：培训信息 ============ -->
      <el-card v-if="isTrain" :header="$t('expense.preapply.secTrainingInfo')" style="margin-top: 12px">
        <el-form ref="trainFormRef" :rules="trainRules" :model="b.form.bill.extJson" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblTrainType')" prop="trainType"><el-select v-model="b.form.bill.extJson.trainType" style="width:100%"><el-option v-for="o in trainingTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblTrainName')" prop="trainName"><el-input v-model="b.form.bill.extJson.trainName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblTrainPlace')" prop="trainPlace"><el-input v-model="b.form.bill.extJson.trainPlace" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colStartDate')" prop="startDate"><el-date-picker v-model="b.form.bill.extJson.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled-date="(d) => dayAfter(d, b.form.bill.extJson.endDate)" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colEndDate')" prop="endDate"><el-date-picker v-model="b.form.bill.extJson.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled-date="(d) => dayBefore(d, b.form.bill.extJson.startDate)" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblTrainDays')" prop="duration"><el-input-number v-model="b.form.bill.extJson.duration" :min="1" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colAttendees')" prop="attendees"><el-input-number v-model="b.form.bill.extJson.attendees" :min="1" style="width:100%" @change="calcTrain(b)" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colTotalCost')"><span style="color:#f59a23;font-weight:600">{{ $t('expense.common.amtYuan', { v1: b.fmt(trainTotal(b)) }) }}</span></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.colCostPerPerson')"><span style="color:#f59a23;font-weight:600">{{ $t('expense.common.amtYuan', { v1: b.fmt(trainTotal(b) / (b.form.bill.extJson.attendees || 1)) }) }}</span></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblPlanned')" prop="planFlag"><el-select v-model="b.form.bill.extJson.planFlag" style="width:100%"><el-option :label="$t('common.yes')" :value="true" /><el-option :label="$t('common.no')" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblHasTravel')" prop="travelFlag"><el-select v-model="b.form.bill.extJson.travelFlag" style="width:100%"><el-option :label="$t('common.yes')" :value="true" /><el-option :label="$t('common.no')" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.preapply.lblAbroadTrip')" prop="abroadFlag"><el-select v-model="b.form.bill.extJson.abroadFlag" style="width:100%"><el-option :label="$t('common.yes')" :value="true" /><el-option :label="$t('common.no')" :value="false" /></el-select></el-form-item></el-col>
            <el-col :xs="24"><el-form-item :label="$t('common.remark')" prop="note"><el-input v-model="b.form.bill.extJson.note" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 培训人员及费用详情 -->
      <el-card v-if="isTrain" :header="$t('expense.preapply.secTraineeExpense')" style="margin-top: 12px">
        <el-table :data="persons" size="small" border :empty-text="$t('common.empty')" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.preapply.colName')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colName') }}</template>
            <template #default="{ row, $index }"><el-select v-model="row.name" filterable allow-create default-first-option style="width:100%"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" :disabled="persons.some((p, i) => i !== $index && p.name === (u.nickName || u.userName))" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colStartDate')" width="130"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colStartDate') }}</template>
            <template #default="{ row }"><el-date-picker v-model="row.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled-date="(d) => dayAfter(d, row.endDate)" @change="calcPerson(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colEndDate')" width="130"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colEndDate') }}</template>
            <template #default="{ row }"><el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled-date="(d) => dayBefore(d, row.startDate)" @change="calcPerson(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colDays')" width="70">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.travelClaim.colDeparture')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.travelClaim.colDeparture') }}</template>
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width:100%"><el-option v-for="c in trainCities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.travelClaim.colDestination')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.travelClaim.colDestination') }}</template>
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width:100%"><el-option v-for="c in trainCities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.preapply.colTripMode')" width="100"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.colTripMode') }}</template>
            <template #default="{ row }"><el-select v-model="row.trafficType" style="width:100%" @change="onTrafficTypeChange(row)"><el-option v-for="o in trafficOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.preapply.lblCabinClass')" width="100"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.preapply.lblCabinClass') }}</template>
            <template #default="{ row }"><el-select v-model="row.trafficClass" style="width:100%"><el-option v-for="o in seatOptionsOf(row.trafficType)" :key="o.value" :label="o.label" :value="o.value" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.tabTrainFee')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.trainFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.preapply.colLongTraffic')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.trafficFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.travelClaim.colHotel')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.hotelFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.preapply.colLocalTraffic')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.localTrafficFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column v-if="b.form.bill.extJson.travelFlag" :label="$t('expense.preapply.colMealAllowance')" width="130">
            <template #default="{ row }"><el-input-number v-model="row.allowanceFee" :min="0" :precision="2" style="width:100%" @change="syncTrainLine(b)" /></template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removePerson($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="persons.push(newPerson())">{{ $t('expense.preapply.btnAddTrainee') }}</el-button>
        <div style="margin-top: 6px; font-size: 12px; color: #909399">{{ $t('expense.preapply.hintTotalCostFormula') }}</div>
      </el-card>

      <!-- ============ 分摊信息 ============ -->
      <!-- D-V4R-42：分摊的"写"权限（exp:share:create/update/delete）只授予财务/共享/审批类角色，普通员工没有 ⇒
           原先这张卡对员工照常渲染、两列还挂必填红星，一保存就红字「缺少权限：exp:share:update」且分摊行不落库。
           现口径：读＝随单据走（exp:bill:list，与单据母表同闸，见 BillShareController.byBill），
           写＝随岗位走（exp:share:update）⇒ 员工看得到这张单已分摊到哪（只读），改不了也不必填；
           新建态还没有分摊行 ⇒ 不渲染这张空卡。 -->
      <el-card v-if="canViewShares && (canEditShares || b.form.shares.length)" :header="$t('expense.preapply.secAllocation')" style="margin-top: 12px">
        <el-form :model="shareForm" :rules="shareTypeRules" label-width="110px" size="default">
          <!-- 单选组恒有默认值，不存在漏填，故不加红星 -->
          <el-form-item :label="$t('expense.claim.colBearingType')" prop="shareType">
            <el-radio-group v-model="shareType" :disabled="!canEditShares">
              <el-radio-button value="COST_CENTER">{{ $t('expense.claim.optCostCenterBearing') }}</el-radio-button>
              <el-radio-button value="PROJECT">{{ $t('expense.claim.optProjectBearing') }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-table :data="b.form.shares" size="small" border :empty-text="$t('common.empty')" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.preapply.colLineNo')" width="70">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column :label="shareType === 'COST_CENTER' ? t('expense.travelClaim.colCostCenter') : t('expense.travelClaim.colProject')" min-width="120">
            <template #default="{ row }">
              <el-select v-if="shareType === 'COST_CENTER'" v-model="row.costCenterId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-for="c in b.costCenters" :key="c.id" :label="ccLabel(c)" :value="c.id" />
              </el-select>
              <el-select v-else v-model="row.projectId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-for="p in b.projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.common.colBudgetItem')" min-width="120"><template #header><span v-if="canEditShares" style="color:#f56c6c">*</span>{{ $t('expense.common.colBudgetItem') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.budgetItemId" :disabled="!canEditShares" filterable style="width: 100%">
                <el-option v-if="row.budgetItemId != null && !b.budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                <el-option v-for="bg in b.budgets" :key="bg.id" :label="budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects })" :value="bg.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.colAllocAmount')" width="130"><template #header><span v-if="canEditShares" style="color:#f56c6c">*</span>{{ $t('expense.preapply.colAllocAmount') }}</template>
            <template #default="{ row }">
              <el-input-number v-model="row.shareAmount" :disabled="!canEditShares" :min="0" :precision="2" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.secBudgetHint')" min-width="140">
            <template #default="{ row }">
              <span v-if="row.budgetItemId" :style="{ color: shareBudget(b, row).over ? '#f56c6c' : '#67c23a' }">
                {{ shareBudget(b, row).over ? t('expense.preapply.errOverQuota') : t('expense.create.stWithinBudget') + b.fmt(shareBudget(b, row).available) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="canEditShares" :label="$t('common.operation')" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeShare(b, $index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
          </el-table-column>
        </el-table>
        <el-button v-if="canEditShares" size="small" style="margin-top: 8px" @click="b.form.shares.push(b.newShare())">{{ $t('expense.preapply.btnAddShareLine') }}</el-button>
        <div style="margin-top: 6px">{{ $t('expense.preapply.labShareTotal') }}<span style="color: #f59a23; font-weight: 600">{{ b.fmt(shareTotal(b)) }}</span> {{ $t('expense.common.unitYuan') }}</div>
        <div v-if="!canEditShares" style="margin-top: 6px; font-size: 12px; color: #909399">
          {{ $t('expense.preapply.hintShareReadonly') }}</div>
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
import { ccLabel, showRowDetail, budgetLabel, budgetUnseenLabel, formatDateTime } from '@/utils/format'
import { userApi, masterApi, configApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { useI18n } from 'vue-i18n'
import { useBaseDict, TRAFFIC_LOCAL, TRAFFIC_I18N, SEAT_LOCAL, SEAT_I18N, TRAINING_LOCAL, TRAINING_I18N } from '@/composables/useBaseDict'
import { useCurrency } from '@/composables/useCurrency'

const { t } = useI18n()
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
const subTypeLabel = computed(() => ({ MEETING: t('expense.preapply.subMeeting'), TRAVEL: t('expense.preapply.subTravel'), TRAIN: t('expense.preapply.subTrain') })[props.subType] || t('expense.preapply.tabRequisition'))

// D-V4R-42：分摊的"写"（exp:share:create/update/delete）授权名册里只有财务/共享/审批类角色（普通员工不在册）；
// "读"随单据走（exp:bill:list，BillShareController.byBill 已改同闸）⇒ 能读才显示、能写才可编辑，
// 员工看到的是只读分摊行＋下方说明，不再挂必填红星、也不再吃 4030。
const canEditShares = computed(() => hasPerm('exp:share:update'))
const canViewShares = computed(() => canEditShares.value || hasPerm('exp:share:list') || hasPerm('exp:bill:list'))

// ---------- 表单校验：登记表口径的通用/年度字段 + 各子类型特有字段 ----------
const formRules = computed(() => ({
  ...billRules('APPLY', props.subType),
  remark: isTrain.value
    ? [{ required: true, message: t('expense.preapply.errBillDescRequiredTrain') }]
    : [{ max: 2000, message: t('expense.preapply.hintMax2000'), trigger: 'blur' }],
  'extJson.supplementApply': [{ required: true, message: t('expense.preapply.errRetroFlagRequired') }],
  'extJson.travelType': [{ required: true, message: t('expense.travelClaim.errTripTypeRequired') }],
  'extJson.travelReason': [{ required: true, message: t('expense.preapply.errTripReasonRequired') }],
  'extJson.overReason': [{ required: true, message: t('expense.preapply.errOverStdRequired') }],
}))
const meetingRules = computed(() => ({
  meetingName: [{ required: true, message: t('expense.preapply.errMeetingNameRequired') }],
  meetingPlace: [{ required: true, message: t('expense.preapply.errMeetingPlaceRequired') }],
  startDate: [{ required: true, message: t('expense.preapply.errStartDateRequired') }],
  endDate: [{ required: true, message: t('expense.preapply.errEndDateRequired') }],
  topic: [{ required: true, message: t('expense.preapply.errMeetingTopicRequired') }],
  attendees: [{ required: true, type: 'number', message: t('expense.preapply.errAttendeesRequired'), trigger: 'change' }],
  meetingAmount: [{ required: true, type: 'number', min: 0.01, message: t('expense.preapply.errMeetingAmountRequired'), trigger: 'change' }],
  note: [{ required: true, message: t('expense.preapply.errRemarkRequired') }],
}))
// 借款信息是出差申请的选填区块：未借款时不该拦提交，填了借款金额才要求还款日期与收款账户（防付错）
function loanRules(extJson) {
  const needLoan = Number(extJson?.loanAmount) > 0
  const req = (msg) => needLoan ? commonRules.required(msg) : []
  return {
    expectRepayDate: needLoan ? commonRules.date(t('expense.preapply.errRepayDateRequired')) : [],
    accountName: req(t('expense.preapply.errPayeeNameRequired')),
    accountNo: req(t('expense.preapply.errPayeeAccountRequired')),
    bankName: req(t('expense.preapply.errBankRequired')),
  }
}
const formRef = ref(null)
const meetingFormRef = ref(null)
const loanFormRef = ref(null)
// 培训信息：此前只有裸 required（红星装饰，一条规则都不跑），现补规则并接入提交闸门
const trainFormRef = ref(null)
const trainRules = computed(() => ({
  trainType: commonRules.select(t('expense.preapply.errTrainTypeRequired')),
  trainName: commonRules.required(t('expense.preapply.errTrainNameRequired')),
  trainPlace: commonRules.required(t('expense.preapply.errTrainPlaceRequired')),
  startDate: commonRules.date(t('expense.preapply.errStartDateRequired')),
  endDate: commonRules.date(t('expense.preapply.errEndDateRequired')),
  duration: [{ required: true, type: 'number', message: t('expense.preapply.errTrainDaysRequired'), trigger: 'change' }],
  attendees: [{ required: true, type: 'number', message: t('expense.preapply.errAttendeesRequired'), trigger: 'change' }],
  planFlag: commonRules.select(t('expense.preapply.errPlannedRequired')),
  travelFlag: commonRules.select(t('expense.preapply.errTravelFlagRequired')),
  abroadFlag: commonRules.select(t('expense.preapply.errAbroadFlagRequired')),
  note: commonRules.required(t('expense.preapply.errRemarkRequired')),
}))

const users = ref([])
const shareType = ref('COST_CENTER')
// 第2批：分摊方式恒有默认值（COST_CENTER）——挂 rules 纳入中央框架视野；
// computed 包装只为给 el-form 一个可校验 model，v-model 仍绑 shareType ref 不动
const shareForm = computed(() => ({ shareType: shareType.value }))
const shareTypeRules = { shareType: commonRules.select('请选择分摊方式') }
const persons = ref([newPerson()])
const calcLoading = ref(false)
const stdCache = ref(null)
// 业务口径：往返是否多算一晚。系统参数 expense.travel.roundTripExtraNight（1=算，0=不算），缺省/读取失败按「算」。
const roundTripExtraNight = ref(true)
async function loadRoundTripConfig() {
  try {
    const v = await configApi.value('expense.travel.roundTripExtraNight')
    if (v != null) roundTripExtraNight.value = !['0', 'false', 'FALSE'].includes(String(v).trim())
  } catch (e) { /* 旧包无该读口或参数未种：保持既有口径（加 1 天） */ }
}

// 差旅标准自动计算：按 城市等级×天数×标准金额 带出 住宿费/餐饮补贴
async function autoCalcTravel(b) {
  if (!b.form.travels.length) { ElMessage.warning(t('expense.preapply.errAddTripFirst')); return }
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
    ElMessage.success(t('expense.preapply.msgAutoTripCalculated', { v1: filled }))
  } catch (e) { ElMessage.error(e.message) } finally { calcLoading.value = false }
}
const currentUserName = ref(store.user?.username || store.user?.userName || t('expense.common.optCurrentUser'))

function newPerson() {
  return { name: '', startDate: '', endDate: '', days: 0, fromCityId: null, toCityId: null, trafficType: 'AIR', trafficClass: 'ECONOMY', trainFee: 0, trafficFee: 0, hotelFee: 0, localTrafficFee: 0, allowanceFee: 0 }
}
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]

// ---------- 金额计算 ----------
function travelTotal(row) {
  return Number(row.trafficAmount || 0) + Number(row.hotelAmount || 0) + Number(row.allowanceAmount || 0) + Number(row.localTrafficAmount || 0) + Number(row.otherAmount || 0)
}
function dayStr(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function dayBefore(d, min) { return !!min && dayStr(d) < min }
function dayAfter(d, max) { return !!max && dayStr(d) > max }
// 行删除确认：只拦「已填内容」的行，空白行直接删
async function confirmRemoveRow(hasContent) {
  if (!hasContent) return true
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
    return true
  } catch { return false }
}
async function removeTravel(b, i) {
  const row = b.form.travels[i]
  if (!row) return
  const has = !!row.beginDate || !!row.endDate || !!row.fromCityId || !!row.toCityId || !!row.destCountry
    || !!row.trafficClass || !!String(row.remark || '').trim()
    || [row.hotelAmount, row.trafficAmount, row.allowanceAmount, row.localTrafficAmount, row.otherAmount].some((n) => Number(n || 0) > 0)
  if (!(await confirmRemoveRow(has))) return
  b.form.travels.splice(i, 1)
}
async function removePerson(i) {
  const p = persons.value[i]
  if (!p) return
  const has = !!String(p.name || '').trim() || !!p.startDate || !!p.endDate || !!p.fromCityId || !!p.toCityId
    || !!p.trafficClass || [p.trainFee, p.trafficFee, p.hotelFee, p.localTrafficFee, p.allowanceFee].some((n) => Number(n || 0) > 0)
  if (!(await confirmRemoveRow(has))) return
  persons.value.splice(i, 1)
}
async function removeShare(b, i) {
  const s = b.form.shares[i]
  if (!s) return
  const has = s.companyId != null || s.deptId != null || s.costCenterId != null || s.projectId != null
    || s.budgetItemId != null || Number(s.shareAmount || 0) > 0 || !!String(s.remark || '').trim()
  if (!(await confirmRemoveRow(has))) return
  b.form.shares.splice(i, 1)
}
function calcTravel(b, row) {
  if (row.beginDate && row.endDate) {
    const d = (new Date(row.endDate) - new Date(row.beginDate)) / 86400000
    // 倒置日期不再折成 0/1 天：那会让"结束早于开始"看起来合法并算出错的标准
    row.days = d >= 0 ? Math.round(d) + (row.roundTrip && roundTripExtraNight.value ? 1 : 0) : null
  }
  row.totalAmount = Math.round(travelTotal(row) * 100) / 100
  syncTravelLine(b)
}
function copyTravel(b, i) {
  const trip = b.form.travels[i]
  // 复制行清空日期：同人同日两段行程＝重复登记（D-TR-01），由用户填新段日期
  b.form.travels.push({ ...trip, id: null, beginDate: '', endDate: '', days: 0 })
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
    const diff = Math.round((new Date(b.form.bill.extJson.endDate) - new Date(b.form.bill.extJson.startDate)) / 86400000) + 1
    b.form.bill.extJson.duration = diff > 0 ? diff : null
  }
  syncTrainLine(b)
}
function calcPerson(b, row) {
  if (row.startDate && row.endDate) {
    const diff = Math.round((new Date(row.endDate) - new Date(row.startDate)) / 86400000) + 1
    row.days = diff > 0 ? diff : null
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
    if (bad) { ElMessage.warning(t('expense.preapply.errTripRequiredMissing')); return false }
    if (b.form.travels.some((t) => t.beginDate && t.endDate && t.beginDate > t.endDate)) {
      ElMessage.warning(t('expense.preapply.errTripDateOrder')); return false
    }
    // D-TR-01 同类口径：同人同日重复行程提交闸
    const dupT = b.form.travels.find((t, i) => travelDup(b, i, t.travelerName))
    if (dupT) { ElMessage.warning(t('expense.preapply.errDuplicateTrip', { v1: dupT.travelerName })); return false }
    if (!(await validateForms([loanFormRef]))) return false
    // 预计还款日不得早于申请日（前端禁选 + 提交兜底）
    const applyDay = b.form.bill.applyDate || b.today()
    const repayDay = b.form.bill.extJson.expectRepayDate
    if (applyDay && repayDay && repayDay < applyDay) {
      ElMessage.warning(t('expense.loan.errRepayBeforeApply')); return false
    }
  }
  if (isTrain.value && !(await validateForms([trainFormRef]))) return false
  if (isMeeting.value && b.form.bill.extJson.startDate && b.form.bill.extJson.endDate && b.form.bill.extJson.startDate > b.form.bill.extJson.endDate) {
    ElMessage.warning(t('expense.preapply.errMeetingDateOrder')); return false
  }
  if (isTrain.value && b.form.bill.extJson.startDate && b.form.bill.extJson.endDate && b.form.bill.extJson.startDate > b.form.bill.extJson.endDate) {
    ElMessage.warning(t('expense.preapply.errTrainDateOrder')); return false
  }
  if (isTrain.value && persons.value.some((p) => p.startDate && p.endDate && p.startDate > p.endDate)) {
    ElMessage.warning(t('expense.preapply.errPersonDateOrder')); return false
  }
  if (isMeeting.value) syncMeetingLine(b)
  if (isTravel.value) syncTravelLine(b)
  if (isTrain.value) {
    // D-TR-01：姓名必填 + 同名唯一（同名重复会把人员费用直接翻倍进单据金额）
    const named = persons.value.map((p) => (p.name || '').trim()).filter(Boolean)
    if (named.length < persons.value.length) { ElMessage.warning(t('expense.preapply.errTraineeNameRequired')); return false }
    const dup = named.find((n, i) => named.indexOf(n) !== i)
    if (dup) { ElMessage.warning(t('expense.preapply.errDuplicateTrainee', { v1: dup })); return false }
    // RA-06 乙案：人数与人员清单不一致仅二次确认，不硬拦（按人头汇总一行是合法用法）
    const att = Number(b.form.bill.extJson.attendees || 0)
    if (att && att !== persons.value.length) {
      try {
        await ElMessageBox.confirm(t('expense.preapply.askAttendeeMismatch', { v1: att, v2: persons.value.length }), t('expense.preapply.errHeadcountMismatch'), { type: 'warning' })
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
    ElMessage.warning(t('expense.preapply.errShareBudgetMixed'))
    return false
  }
  return true
}

// ---------- 超标 ----------
function overContent(b) {
  if (isTravel.value) {
    const overRows = b.form.travels.filter((t) => t.overStd === 1)
    if (overRows.length) return t('expense.preapply.errTripsOverStd', { v1: overRows.length })
  }
  return b.form.lines.some((l) => l.overStd === 1) ? t('expense.preapply.errLinesOverStd') : ''
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
    // P0 修复（2026-10-21）：默认只加载同公司人员
    const list = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
    users.value = list
  } catch (e) { users.value = [] }
}
function onApplicantChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}
// 出差人员下拉只绑姓名 ⇒ 补一行反查，把 exp_travel.traveler_id 填上（此前恒 NULL）
function onTravelerPick(row) {
  const u = users.value.find((x) => (x.nickName || x.userName) === row.travelerName)
  row.travelerId = u ? u.id : null
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
  // 行详情弹窗（双击行程/培训人员/分摊行触发）：
  // 此前直接把行对象摊开，键名走 prettyKey 回落成 "Traffic Type / Allowance Amount" 英文标题、
  // 值原样输出 RAIL / SECOND / true —— 中文界面里完全没法看。
  // 现按「中文标签 + 字典文案」渲染：交通方式/舱位席别/培训类型取后台已加载字典（与下拉同源），
  // 字典未到则 format.js 内置映射兜底；name 键在人员行是姓名（全局登记会被其它模块误用，故此处显式指定）。
  return showRowDetail(row, {
    title: t('field.detail'),
    labels: { name: t('expense.preapply.colName') },
    enums: {
      trafficType: (v) => trafficDict.labelOf(v),
      tripMode: (v) => trafficDict.labelOf(v),
      trafficClass: (v) => seatDict.labelOf(v),
      trainType: (v) => trainingTypeDict.labelOf(v),
    },
  })
}

const route = useRoute()
const router = useRouter()
// 出差类型（travel_type）：字典定值域 + i18n 定文案
const travelTypeDict = useBaseDict('travel_type',
  { DOMESTIC: '境内', ABROAD: '境外', TRIAREA: '三地' },
  { DOMESTIC: 'expense.preapply.optDomesticTrip', ABROAD: 'expense.preapply.optAbroadTrip', TRIAREA: 'expense.preapply.optThreeCityTrip' })
const travelTypeOptions = travelTypeDict.options

// ---------- 城市联动：出发地/目的地随 境内/境外 过滤 ----------
// md_city.is_abroad（0=国内，1=国外）。境内(境内)只显示国内城市、境外(境外)只显示国外城市，三地显示全部；
// 培训页按 extJson.abroadFlag 同步过滤。切换类型时清空已选城市，避免脏值残留（选了国内城市后切到境外，旧值不在新范围内）。
const travelCities = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return []
  const tt = b.form.bill.extJson.travelType
  if (tt === 'ABROAD') return b.cities.filter((c) => c.isAbroad === 1)
  if (tt === 'DOMESTIC') return b.cities.filter((c) => c.isAbroad === 0)
  return b.cities
})
const trainCities = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return []
  const ab = b.form.bill.extJson.abroadFlag
  if (ab === true) return b.cities.filter((c) => c.isAbroad === 1)
  if (ab === false) return b.cities.filter((c) => c.isAbroad === 0)
  return b.cities
})
watch(() => {
  const b = wb.value && wb.value.b
  return b ? b.form.bill.extJson.travelType : null
}, (nv, ov) => {
  if (ov == null || nv === ov) return
  const b = wb.value && wb.value.b
  if (!b) return
  b.form.travels.forEach((t) => { t.fromCityId = null; t.toCityId = null })
})

// 交通方式 / 舱位席别 / 培训类型（2026-10-18 基础数据收口，值域改为后台可维护）
const trafficDict = useBaseDict('traffic_mode', TRAFFIC_LOCAL, TRAFFIC_I18N)
const trafficOptions = trafficDict.options
const seatDict = useBaseDict('seat_class', SEAT_LOCAL, SEAT_I18N)
const seatOptions = seatDict.options
// 座位等级随交通工具变化（新机制：只在既有 seat_class 字典上做取值窄化）
const SEAT_CABIN = ['ECONOMY', 'BUSINESS', 'FIRST']
const SEAT_RAIL = ['FIRST_SEAT', 'SECOND']
function seatOptionsOf(trafficType) {
  const all = seatOptions.value
  if (trafficType === 'AIR' || trafficType === 'SHIP') return all.filter((o) => SEAT_CABIN.includes(o.value))
  if (trafficType === 'RAIL' || trafficType === 'TRAIN') return all.filter((o) => SEAT_RAIL.includes(o.value))
  return all // CAR 等：字典里没有该方式的专属席别 ⇒ 保持全量，缺项待补（见登记）
}
// 换交通工具后，原席别若已不在合法集合里就清空（留着错配继续提交）
function onTrafficTypeChange(row) {
  const keep = seatOptionsOf(row.trafficType)
  if (row.trafficClass && !keep.some((o) => o.value === row.trafficClass)) row.trafficClass = ''
}
const trainingTypeDict = useBaseDict('training_type', TRAINING_LOCAL, TRAINING_I18N)
const trainingTypeOptions = trainingTypeDict.options

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency({ i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }, format: 'name-code' })

onMounted(async () => {
  loadCurrency()
  await loadRoundTripConfig()
  travelTypeDict.load()
  // 【2026-10-18 基础数据收口】交通方式/舱位席别/培训类型改为读 sys_dict，
  // 字典不可用时 useBaseDict 自动回落到本地常量，界面与改造前逐字一致。
  trafficDict.load()
  seatDict.load()
  trainingTypeDict.load()
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单（子类型页面壳由路由保证）
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      router.replace({ query: {} })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  await loadUsers()
currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || t('expense.common.optCurrentUser')
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
      if (uid != null) ElMessage.warning(t('expense.common.errApplicantNotMatched'))
    }
  }
  if (b && b.form.bill.title === '') b.form.bill.title = subTypeLabel.value
})
</script>
