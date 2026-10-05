<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="card-title">
            争议复核单
            <el-button type="primary" size="small" style="float:right" @click="openCreate">货主发起复核</el-button>
          </div>
          <el-radio-group v-model="filter" size="small" style="margin:8px 0">
            <el-radio-button value="OPEN">待复核</el-radio-button>
            <el-radio-button value="CONCLUDED">已结案</el-radio-button>
            <el-radio-button value="">全部</el-radio-button>
          </el-radio-group>
          <el-table :data="reviews" size="small" highlight-current-row max-height="470"
                    @current-change="onSelect">
            <el-table-column prop="reviewNo" label="复核单号" width="150" class="mono" />
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'CONCLUDED' ? 'success' : 'danger'">
                  {{ $dict('ReviewStatus', row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="货主主张">
              <template #default="{ row }">
                {{ row.ownerClaimLossRate != null ? (row.ownerClaimLossRate*100).toFixed(1)+'%' : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-top:12px">
          <div class="card-title">考核与赔付</div>
          <el-button size="small" style="width:100%;margin-bottom:8px" @click="perfVisible = true">
            质检员考核（{{ perfSummary.length }} 人）
          </el-button>
          <el-button size="small" style="width:100%;margin-bottom:8px" @click="teamVisible = true">
            分拣班组考核（{{ teamSummary.length }} 组）
          </el-button>
          <el-button size="small" style="width:100%" @click="compVisible = true">
            客户赔付台账（{{ compensations.length }} 条）
          </el-button>
        </el-card>
      </el-col>

      <el-col :span="18" v-if="d">
        <!-- 平台证据展示 -->
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              {{ d.review.reviewNo }} — 平台证据（向货主公开）
              <el-tag size="small" :type="d.review.status === 'CONCLUDED' ? 'success' : 'danger'">
                {{ $dict('ReviewStatus', d.review.status) }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="4" size="small" border>
            <el-descriptions-item label="批次号">{{ d.batch.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="品类">{{ catName(d.batch.categoryId) }}</el-descriptions-item>
            <el-descriptions-item label="货主">{{ ownerName(d.batch.ownerId) }}</el-descriptions-item>
            <el-descriptions-item label="分拣单号">{{ d.sorting.sortingNo }}</el-descriptions-item>
            <el-descriptions-item label="原始入库毛重">{{ d.batch.grossWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="皮重">{{ d.batch.tareWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="原始入库净重">
              <b class="risk-warn">{{ d.batch.netWeight }}kg</b>
            </el-descriptions-item>
            <el-descriptions-item label="到货温度">{{ d.batch.arrivalTemp }}℃</el-descriptions-item>
            <el-descriptions-item label="分拣投入">{{ d.sorting.inputWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="分拣可售产出">{{ d.sorting.outputWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="登记损耗">
              <b class="risk-critical">{{ d.sorting.lossWeight }}kg（{{ (d.sorting.lossRate*100).toFixed(1) }}%）</b>
            </el-descriptions-item>
            <el-descriptions-item label="货主主张">
              损耗率 ≤ {{ d.review.ownerClaimLossRate != null ? (d.review.ownerClaimLossRate*100).toFixed(1)+'%' : '未填' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-row :gutter="12" style="margin-top:12px">
            <el-col :span="6">
              <div class="evidence-box">
                <div class="ev-title">分拣视频</div>
                <video v-if="isHttp(d.sorting.sortVideoUrl)" :src="d.sorting.sortVideoUrl" controls
                       style="width:100%;border-radius:6px" />
                <div v-else class="ev-ph">
                  <el-icon size="26"><VideoCamera /></el-icon>
                  <div class="ev-code">{{ d.sorting.sortVideoUrl || '未上传' }}</div>
                  <div class="hint">工位：{{ d.sorting.sortStation || '-' }} · {{ d.sorting.sortedAt }}</div>
                </div>
              </div>
            </el-col>
            <el-col :span="9">
              <div class="evidence-box">
                <div class="ev-title">坏果照片</div>
                <div class="photo-row">
                  <template v-if="photoList.length">
                    <div v-for="(p, i) in photoList" :key="i" class="photo-item">
                      <el-image v-if="isHttp(p)" :src="p" fit="cover" style="width:86px;height:64px" />
                      <div v-else class="ev-ph small">
                        <el-icon><Picture /></el-icon><span class="ev-code">{{ p }}</span>
                      </div>
                    </div>
                  </template>
                  <span v-else class="hint">未上传坏果照片</span>
                </div>
              </div>
            </el-col>
            <el-col :span="9">
              <div class="evidence-box">
                <div class="ev-title">等级划分 &amp; 质检员签名</div>
                <el-table :data="d.gradeItems" size="small" border>
                  <el-table-column label="等级" width="88">
                    <template #default="{ row }">{{ $dict('Grade', row.grade) }}</template>
                  </el-table-column>
                  <el-table-column prop="weight" label="重量kg" width="70" />
                  <el-table-column prop="unitPrice" label="元/kg" width="70" />
                </el-table>
                <div class="sign-row" v-if="d.inspection">
                  <el-tag size="small" type="success">质检员签名</el-tag>
                  <span class="sign-text">{{ d.inspection.signature || d.inspection.inspector }}</span>
                  <span class="hint">
                    外观{{ d.inspection.appearanceScore }} 腐损率{{ ((d.inspection.rotRate||0)*100).toFixed(1) }}%
                    复测{{ d.inspection.measuredTemp }}℃
                  </span>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 复核作业 -->
        <el-card shadow="never" style="margin-top:12px" v-if="d.review.status !== 'CONCLUDED'">
          <div class="card-title">复核作业 — {{ d.review.reviewer }}</div>
          <el-row :gutter="12">
            <el-col :span="11">
              <div class="sub-title">重新抽样</div>
              <el-form label-width="92px" size="small">
                <el-form-item label="取样点/筐号">
                  <el-input v-model="sample.samplePoint" placeholder="如 三号位/筐 K-07" />
                </el-form-item>
                <el-form-item label="抽样重量kg">
                  <el-input-number v-model="sample.sampleWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="坏果重量kg">
                  <el-input-number v-model="sample.rotWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="A级kg">
                  <el-input-number v-model="sample.gradeAWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="B级kg">
                  <el-input-number v-model="sample.gradeBWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="加工级kg">
                  <el-input-number v-model="sample.gradeProcessWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="sample.remark" type="textarea" :rows="2" />
                </el-form-item>
                <el-button type="primary" size="small" @click="addSample">提交抽样（可多次）</el-button>
              </el-form>
            </el-col>
            <el-col :span="13">
              <div class="sub-title">抽样记录与推算口径</div>
              <el-table :data="d.samples" size="small" border>
                <el-table-column prop="sampleSeq" label="#" width="42" />
                <el-table-column prop="samplePoint" label="取样点" width="100" />
                <el-table-column prop="sampleWeight" label="抽样kg" width="72" />
                <el-table-column prop="rotWeight" label="坏果kg" width="72" />
                <el-table-column label="坏果率" width="80">
                  <template #default="{ row }">
                    {{ (Number(row.rotWeight)/Number(row.sampleWeight)*100).toFixed(1) }}%
                  </template>
                </el-table-column>
              </el-table>
              <el-descriptions :column="2" size="small" border style="margin-top:10px">
                <el-descriptions-item label="累计抽样坏果率">
                  <b>{{ ((d.review.sampleRotRate||0)*100).toFixed(1) }}%</b>
                </el-descriptions-item>
                <el-descriptions-item label="货主主张上限">
                  {{ d.review.ownerClaimLossRate != null ? (d.review.ownerClaimLossRate*100).toFixed(1)+'%' : '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="推算复核损耗">{{ d.review.reviewedLossWeight || 0 }}kg</el-descriptions-item>
                <el-descriptions-item label="调整后可售重量">
                  <b class="ok-text">{{ d.review.reviewedSaleableWeight || 0 }}kg</b>
                </el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>

          <el-divider content-position="left">周转筐证据链：筐号 ↔ 分拣工位 ↔ 搬运时间</el-divider>
          <div style="margin-bottom:8px">
            <el-button size="small" @click="handlingVisible = true">录入搬运时间线</el-button>
            <el-select v-model="pickCrate" placeholder="从搬运记录加入集中筐" size="small"
                       style="width:260px;margin-left:8px" @change="addCrateFromHandling">
              <el-option v-for="h in d.crateHandling" :key="h.id"
                         :label="`${h.crateNo}｜${h.sortStation||'-'}｜到货 ${fmt(h.arrivedAt)}`"
                         :value="h.crateNo" />
            </el-select>
            <el-button size="small" @click="addEmptyCrate">手工加筐</el-button>
          </div>
          <el-table :data="crates" size="small" border>
            <el-table-column label="筐号" width="110">
              <template #default="{ row }"><el-input v-model="row.crateNo" size="small" /></template>
            </el-table-column>
            <el-table-column label="坏果kg" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.badFruitWeight" :min="0" :precision="2" size="small"
                                 controls-position="right" style="width:90px" />
              </template>
            </el-table-column>
            <el-table-column label="分拣工位" width="120">
              <template #default="{ row }"><el-input v-model="row.sortStation" size="small" /></template>
            </el-table-column>
            <el-table-column label="坏果形态/挤压痕迹" min-width="170">
              <template #default="{ row }"><el-input v-model="row.damagePattern" size="small"
                placeholder="如 底层挤压破皮、掉粒、萎蔫" /></template>
            </el-table-column>
            <el-table-column label="指向成因（空=自动判定）" width="180">
              <template #default="{ row }">
                <el-select v-model="row.indicatedCause" size="small" clearable placeholder="按时间线自动">
                  <el-option label="到货质量差" value="ARRIVAL_QUALITY" />
                  <el-option label="装卸挤压" value="LOADING_SQUEEZE" />
                  <el-option label="分拣台长时间暴露" value="SORT_EXPOSURE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column width="50">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="crates.splice($index,1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint" style="margin-top:6px">
            自动判定规则：到台后 ≥30 分钟未开拣 → 分拣台长时间暴露；底层筐/码高 ≥6 层且有挤压破皮 → 装卸挤压；否则对照坏果形态判到货质量。
          </div>

          <el-divider content-position="left">复核结论</el-divider>
          <el-form label-width="120px" size="small">
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="损耗成因">
                  <el-radio-group v-model="conclude.lossCause">
                    <el-radio value="ARRIVAL_QUALITY">到货质量差（货主承担）</el-radio>
                    <el-radio value="LOADING_SQUEEZE">装卸挤压（冷库赔付）</el-radio>
                    <el-radio value="SORT_EXPOSURE">分拣台暴露（冷库赔付）</el-radio>
                    <el-radio value="MIXED">多因混合</el-radio>
                    <el-radio value="NORMAL">正常自然损耗（货主承担）</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="责任归属">
                  <el-select v-model="conclude.responsibility" placeholder="按成因自动判定" clearable>
                    <el-option label="货主承担" value="OWNER" />
                    <el-option label="冷库赔付" value="WAREHOUSE" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="8">
                <el-form-item label="调整可售kg">
                  <el-input-number v-model="conclude.reviewedSaleableWeight" :min="0"
                                   :precision="2" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="责任班组">
                  <el-input v-model="conclude.sortTeam" :placeholder="d.sorting.sortStation ? d.sorting.sortStation+' 分拣班组' : '分拣班组'" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="传导客户(可空)">
                  <el-select v-model="conclude.customerId" clearable placeholder="默认赔付货主">
                    <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="坏果集中">
              <el-switch v-model="conclude.crateConcentrated"
                         active-text="坏果集中在上述周转筐（筐号/工位/搬运时间已串联）" />
            </el-form-item>
            <el-form-item label="结论说明">
              <el-input v-model="conclude.conclusion" type="textarea" :rows="2"
                        placeholder="抽样依据、筐证据链分析、责任与赔付口径" />
            </el-form-item>
            <el-form-item label="复核人签名">
              <el-input v-model="conclude.reviewerSignature" style="width:260px" placeholder="手写签名/签名编号" />
            </el-form-item>
            <el-button type="primary" @click="submitConclude" :loading="saving">提交复核结论并回写结算/考核</el-button>
          </el-form>
        </el-card>

        <!-- 结案结果 -->
        <el-card shadow="never" style="margin-top:12px" v-else>
          <div class="card-title">复核结论（已回写结算单与质检员考核）</div>
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="损耗成因">
              <el-tag type="warning">{{ $dict('LossCause', d.review.lossCause) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="责任归属">
              <el-tag :type="d.review.responsibility === 'WAREHOUSE' ? 'danger' : 'info'">
                {{ $dict('Responsibility', d.review.responsibility) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="复核人签名">{{ d.review.reviewerSignature }}</el-descriptions-item>
            <el-descriptions-item label="复核损耗">{{ d.review.reviewedLossWeight }}kg（货主承担）</el-descriptions-item>
            <el-descriptions-item label="冷库承担重量">{{ d.review.warehouseBorneWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="冷库赔付金额">
              <b class="risk-warn">{{ d.review.compensateAmount }} 元</b>
            </el-descriptions-item>
            <el-descriptions-item label="调整后可售重量">{{ d.review.reviewedSaleableWeight }}kg</el-descriptions-item>
            <el-descriptions-item label="质检员考核">
              {{ d.review.inspectorName }}：
              <el-tag :type="d.review.inspectorDeductedPoints > 0 ? 'danger' : 'success'" size="small">
                {{ d.review.inspectorDeductedPoints > 0
                    ? '到货质量漏检扣 '+d.review.inspectorDeductedPoints+' 分' : '判定一致，不扣分' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="坏果集中">
              {{ d.review.crateConcentrated ? '集中（见证据链）' : '无明显集中' }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="d.review.crateAnalysis" class="crate-analysis">
            <el-icon><Link /></el-icon><b>周转筐证据链：</b>{{ d.review.crateAnalysis }}
          </div>
          <div v-if="d.compensation" class="crate-analysis">
            <el-icon><Money /></el-icon>
            赔付记录 {{ d.compensation.compensationNo }}：受偿方
            {{ d.compensation.customerId ? customerName(d.compensation.customerId) : '货主' }}，
            责任班组 {{ d.compensation.sortTeam }}，{{ d.compensation.amount }} 元，
            {{ d.compensation.billWritten ? '已进入货主结算单' : '待生成账单时进入结算单' }}。
          </div>
          <div class="crate-analysis" v-if="d.review.conclusion">
            <el-icon><Document /></el-icon>{{ d.review.conclusion }}
          </div>
        </el-card>
      </el-col>

      <el-col :span="18" v-else>
        <el-empty description="选择左侧复核单，或由货主就争议分拣单发起复核" />
      </el-col>
    </el-row>

    <!-- 发起复核 -->
    <el-dialog v-model="createVisible" title="货主发起分拣损耗争议复核" width="520px">
      <el-form label-width="110px">
        <el-form-item label="争议分拣单">
          <el-select v-model="createForm.sortingId" placeholder="选择存在货主争议的分拣单" style="width:100%">
            <el-option v-for="s in disputedSortings" :key="s.id"
                       :label="`${s.sortingNo}｜损耗 ${s.lossWeight}kg（${(s.lossRate*100).toFixed(1)}%）`"
                       :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主张损耗率上限">
          <el-input-number v-model="createPct" :min="0" :max="100" :precision="1" /> %
        </el-form-item>
        <el-form-item label="货主说明">
          <el-input v-model="createForm.ownerClaimNote" type="textarea" :rows="2"
                    placeholder="如：运输掉粒属正常，损耗应不超过3%" />
        </el-form-item>
        <el-form-item label="复核员">
          <el-input v-model="createForm.reviewer" placeholder="默认 复核员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">发起复核</el-button>
      </template>
    </el-dialog>

    <!-- 录入搬运时间线 -->
    <el-dialog v-model="handlingVisible" title="周转筐装卸/搬运时间线" width="640px">
      <el-form label-width="100px" v-if="d" size="small">
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="筐号"><el-input v-model="handling.crateNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分拣工位"><el-input v-model="handling.sortStation" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="堆层层序">
            <el-input-number v-model="handling.stackLayer" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="码放层高">
            <el-input-number v-model="handling.stackHeight" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到货时间">
            <el-date-picker v-model="handling.arrivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                            style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="卸车时间">
            <el-date-picker v-model="handling.unloadedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                            style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到分拣台">
            <el-date-picker v-model="handling.reachedStationAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                            style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开拣时间">
            <el-date-picker v-model="handling.sortingStartedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                            style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="搬运工"><el-input v-model="handling.handler" /></el-form-item>
      </el-form>
      <el-table :data="d?.crateHandling || []" size="small" max-height="220" style="margin-top:8px">
        <el-table-column prop="crateNo" label="筐号" width="80" />
        <el-table-column prop="sortStation" label="工位" width="100" />
        <el-table-column label="层序/层高" width="80">
          <template #default="{ row }">{{ row.stackLayer }}/{{ row.stackHeight }}</template>
        </el-table-column>
        <el-table-column label="到货→开拣">
          <template #default="{ row }">{{ fmt(row.arrivedAt) }} → {{ fmt(row.sortingStartedAt) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="handlingVisible = false">关闭</el-button>
        <el-button type="primary" @click="submitHandling">保存时间线</el-button>
      </template>
    </el-dialog>

    <!-- 考核 -->
    <el-dialog v-model="perfVisible" title="质检员考核（争议复核回写）" width="640px">
      <el-table :data="perfSummary" size="small" border>
        <el-table-column prop="inspector" label="质检员" />
        <el-table-column prop="reviewCount" label="复核次数" width="90" />
        <el-table-column prop="missJudgeCount" label="漏检次数" width="90" />
        <el-table-column prop="deductedPoints" label="累计扣分" width="90" />
        <el-table-column label="当前分值" width="100">
          <template #default="{ row }">
            <el-tag :type="row.score >= 90 ? 'success' : row.score >= 70 ? 'warning' : 'danger'" size="small">
              {{ row.score }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <el-table :data="perfList" size="small" max-height="260">
        <el-table-column prop="occurredAt" label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column prop="inspector" label="质检员" width="90" />
        <el-table-column prop="reviewNo" label="复核单" width="140" class="mono" />
        <el-table-column prop="detail" label="考核事项" min-width="220" />
        <el-table-column label="扣分" width="70">
          <template #default="{ row }">
            <span :class="row.deductedPoints > 0 ? 'risk-critical' : 'ok-text'">
              {{ row.deductedPoints }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 分拣班组考核 -->
    <el-dialog v-model="teamVisible" title="分拣班组考核（冷库责任复核回写）" width="560px">
      <el-table :data="teamSummary" size="small" border>
        <el-table-column prop="sortTeam" label="责任班组" min-width="160" />
        <el-table-column prop="caseCount" label="冷库责任件数" width="110" />
        <el-table-column prop="borneWeight" label="承担重量kg" width="110" />
        <el-table-column prop="compensateAmount" label="赔付金额元" width="110" />
      </el-table>
    </el-dialog>

    <!-- 赔付台账 -->
    <el-dialog v-model="compVisible" title="客户/货主赔付记录台账" width="760px">      <el-table :data="compensations" size="small" border>
        <el-table-column prop="compensationNo" label="赔付单号" width="150" class="mono" />
        <el-table-column label="成因" width="130">
          <template #default="{ row }">{{ $dict('LossCause', row.lossCause) }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="重量kg" width="80" />
        <el-table-column prop="amount" label="金额元" width="90" />
        <el-table-column prop="sortTeam" label="责任班组" width="130" />
        <el-table-column label="受偿方" width="100">
          <template #default="{ row }">{{ row.customerId ? customerName(row.customerId) : '货主' }}</template>
        </el-table-column>
        <el-table-column label="入结算单" width="90">
          <template #default="{ row }">
            <el-tag :type="row.billWritten ? 'success' : 'info'" size="small">
              {{ row.billWritten ? '已入账' : '待入账' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const route = useRoute()
const filter = ref('OPEN')
const reviews = ref([])
const detailId = ref(null)
const d = ref(null)
const saving = ref(false)
const batches = ref([])
const owners = ref([])
const customers = ref([])
const disputedSortings = ref([])

const createVisible = ref(false)
const handlingVisible = ref(false)
const perfVisible = ref(false)
const teamVisible = ref(false)
const compVisible = ref(false)
const perfList = ref([])
const perfSummary = ref([])
const teamSummary = ref([])
const compensations = ref([])
const pickCrate = ref('')
const categories = ref([])

const createForm = reactive({ sortingId: null, ownerClaimLossRate: 0.03, ownerClaimNote: '', reviewer: '' })
const createPct = ref(3)
const sample = reactive({
  samplePoint: '', sampleWeight: 0, rotWeight: 0,
  gradeAWeight: 0, gradeBWeight: 0, gradeProcessWeight: 0, remark: ''
})
const crates = ref([])
const conclude = reactive({
  lossCause: 'ARRIVAL_QUALITY', responsibility: '', reviewedSaleableWeight: 0,
  crateConcentrated: false, conclusion: '', reviewerSignature: '', sortTeam: '', customerId: null
})
const handling = reactive({
  crateNo: '', sortStation: '', stackLayer: 1, stackHeight: 1,
  arrivedAt: null, unloadedAt: null, reachedStationAt: null, sortingStartedAt: null, handler: ''
})

const photoList = computed(() =>
  (d.value?.sorting.badFruitPhotos || '').split(',').map(s => s.trim()).filter(Boolean))

function isHttp(u) { return typeof u === 'string' && /^https?:\/\//i.test(u) }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }
function catName(id) { return categories.value.find(c => c.id === id)?.name || id }
function ownerName(id) { return owners.value.find(o => o.id === id)?.name || '#' + id }
function customerName(id) { return customers.value.find(c => c.id === id)?.name || '#' + id }

async function loadReviews() {
  reviews.value = await api.get('/api/loss-reviews', { params: { status: filter.value } })
  const target = Number(route.query.id) || detailId.value || reviews.value[0]?.id
  if (target) await select(target)
}

async function onSelect(row) {
  if (!row) return
  await select(row.id)
}

async function select(id) {
  detailId.value = id
  d.value = await api.get('/api/loss-reviews/' + id)
  conclude.reviewedSaleableWeight = Number(d.value.review.reviewedSaleableWeight || 0)
  conclude.lossCause = d.value.review.lossCause || 'ARRIVAL_QUALITY'
  conclude.responsibility = d.value.review.responsibility || ''
  conclude.sortTeam = d.value.sorting.sortStation ? d.value.sorting.sortStation + ' 分拣班组' : ''
  crates.value = (d.value.crateEvidences || []).map(c => ({
    crateNo: c.crateNo, badFruitWeight: Number(c.badFruitWeight),
    sortStation: c.sortStation, damagePattern: c.damagePattern,
    indicatedCause: c.indicatedCause
  }))
}

function openCreate() {
  createForm.sortingId = null
  createForm.ownerClaimNote = ''
  createForm.reviewer = ''
  createPct.value = 3
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.sortingId) {
    ElMessage.warning('请选择争议分拣单')
    return
  }
  const r = await api.post('/api/loss-reviews', {
    sortingId: createForm.sortingId,
    ownerClaimLossRate: Number((createPct.value / 100).toFixed(4)),
    ownerClaimNote: createForm.ownerClaimNote,
    reviewer: createForm.reviewer
  })
  ElMessage.success('复核单已创建，平台证据已向货主展示')
  createVisible.value = false
  filter.value = 'OPEN'
  await loadReviews()
  await select(r.id)
}

async function addSample() {
  if (Number(sample.sampleWeight) <= 0) {
    ElMessage.warning('抽样重量必须大于 0')
    return
  }
  saving.value = true
  try {
    await api.post(`/api/loss-reviews/${d.value.review.id}/samples`, { ...sample })
    ElMessage.success('抽样已记录，复核损耗与可售重量已按累计坏果率推算')
    Object.assign(sample, {
      samplePoint: '', sampleWeight: 0, rotWeight: 0,
      gradeAWeight: 0, gradeBWeight: 0, gradeProcessWeight: 0, remark: ''
    })
    await select(d.value.review.id)
  } finally {
    saving.value = false
  }
}

function addEmptyCrate() {
  crates.value.push({ crateNo: '', badFruitWeight: 0, sortStation: d.value?.sorting.sortStation || '',
    damagePattern: '', indicatedCause: null })
}

function addCrateFromHandling(crateNo) {
  if (!crateNo) return
  if (crates.value.some(c => c.crateNo === crateNo)) {
    pickCrate.value = ''
    return
  }
  const h = d.value.crateHandling.find(x => x.crateNo === crateNo)
  crates.value.push({
    crateNo, badFruitWeight: 0, sortStation: h?.sortStation || d.value.sorting.sortStation || '',
    damagePattern: '', indicatedCause: null
  })
  pickCrate.value = ''
}

async function submitHandling() {
  if (!handling.crateNo) {
    ElMessage.warning('请填写筐号')
    return
  }
  await api.post('/api/review-support/crate-handling', {
    ...handling,
    sortingId: d.value.sorting.id,
    batchId: d.value.batch.id
  })
  ElMessage.success('搬运时间线已保存')
  handlingVisible.value = false
  await select(d.value.review.id)
}

async function submitConclude() {
  if (!d.value.samples.length) {
    ElMessage.warning('请至少完成一次重新抽样')
    return
  }
  saving.value = true
  try {
    await api.post(`/api/loss-reviews/${d.value.review.id}/conclude`, {
      lossCause: conclude.lossCause,
      responsibility: conclude.responsibility || null,
      reviewedSaleableWeight: conclude.reviewedSaleableWeight,
      crateConcentrated: conclude.crateConcentrated,
      conclusion: conclude.conclusion,
      reviewerSignature: conclude.reviewerSignature,
      sortTeam: conclude.sortTeam || null,
      customerId: conclude.customerId,
      crates: crates.value.filter(c => c.crateNo)
    })
    ElMessage.success('复核结论已提交：结算单、质检员考核、客户赔付记录已回写')
    await Promise.all([loadReviews(), loadSideRecords()])
    await select(detailId.value)
  } finally {
    saving.value = false
  }
}

async function loadDisputedSortings() {
  const list = []
  for (const b of batches.value) {
    const ss = await api.get('/api/sortings/batch/' + b.id)
    list.push(...ss.filter(s => s.disputed && !s.disputeResolved))
  }
  disputedSortings.value = list
}

async function loadSideRecords() {
  const [p, ps, ts, c] = await Promise.all([
    api.get('/api/review-support/inspector-performance'),
    api.get('/api/review-support/inspector-performance/summary'),
    api.get('/api/review-support/team-performance'),
    api.get('/api/review-support/compensations')
  ])
  perfList.value = p
  perfSummary.value = ps
  teamSummary.value = ts
  compensations.value = c
}

onMounted(async () => {
  categories.value = await api.get('/api/masters/categories')
  const [o, cs, b] = await Promise.all([
    api.get('/api/masters/owners'),
    api.get('/api/masters/customers'),
    api.get('/api/batches')
  ])
  owners.value = o
  customers.value = cs
  batches.value = b
  await loadDisputedSortings()
  await loadReviews()
  await loadSideRecords()

  // 从分拣页"争议复核"按钮带入分拣单：直接打开发起对话框并预选
  const sortingId = Number(route.query.sortingId)
  if (sortingId && disputedSortings.value.some(s => s.id === sortingId)) {
    createForm.sortingId = sortingId
    createVisible.value = true
  }
})

watch(filter, async () => {
  detailId.value = null
  d.value = null
  await loadReviews()
})
</script>

<style scoped>
.evidence-box {
  border: 1px solid #e6ebf0; border-radius: 8px; padding: 8px 10px; min-height: 118px;
}
.ev-title { font-weight: 600; font-size: 13px; margin-bottom: 6px; color: #303a45; }
.ev-ph {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; min-height: 74px; background: #f4f6f8; border-radius: 6px; color: #8893a0;
}
.ev-ph.small { width: 86px; height: 64px; font-size: 11px; text-align: center; padding: 4px; }
.ev-code { font-size: 12px; font-family: monospace; }
.photo-row { display: flex; flex-wrap: wrap; gap: 6px; }
.sign-row { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.sign-text {
  font-family: "Kaiti SC", "STKaiti", cursive; font-size: 17px; font-weight: 700; color: #1f6f54;
  border-bottom: 1px dashed #9bb8ac; padding: 0 10px;
}
.sub-title { font-weight: 600; font-size: 13px; margin-bottom: 8px; color: #303a45; }
.crate-analysis {
  margin-top: 10px; background: #f7f9fa; border-radius: 8px; padding: 8px 12px;
  font-size: 13px; line-height: 1.8; color: #3a4654;
}
.hint { color: #909daa; font-size: 12px; }
</style>
