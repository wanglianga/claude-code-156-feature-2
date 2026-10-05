<template>
  <div class="page">
    <el-tabs v-model="tab" class="review-tabs">
      <!-- ================= 复核工作台 ================= -->
      <el-tab-pane label="复核工作台" name="work">
        <el-row :gutter="12">
          <el-col :span="7">
            <el-card shadow="never">
              <div class="card-title">
                损耗争议复核单
                <el-button type="primary" size="small" style="float:right" @click="openApply">发起复核</el-button>
              </div>
              <el-radio-group v-model="scope" size="small" style="margin-bottom:8px" @change="loadList">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="open">待处理</el-radio-button>
              </el-radio-group>
              <el-table :data="list" size="small" highlight-current-row @current-change="onSelect"
                        :row-class-name="r => r.id === currentId ? 'sel' : ''">
                <el-table-column prop="reviewNo" label="复核单号" width="165" class="mono" />
                <el-table-column label="批次" width="150">
                  <template #default="{ row }">{{ batchNo(row.batchId) }}</template>
                </el-table-column>
                <el-table-column label="登记损耗" width="80">
                  <template #default="{ row }">{{ row.sortingLossWeight }}kg</template>
                </el-table-column>
                <el-table-column label="状态">
                  <template #default="{ row }">
                    <el-tag size="small" :type="statusType(row.status)">{{ $dict('ReviewStatus', row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>

          <el-col :span="17" v-if="detail">
            <!-- 证据快照 -->
            <el-card shadow="never" style="margin-bottom:12px">
              <div class="card-title">
                平台证据 — {{ detail.review.reviewNo }}
                <el-tag size="small" :type="statusType(detail.review.status)">
                  {{ $dict('ReviewStatus', detail.review.status) }}
                </el-tag>
              </div>
              <el-descriptions :column="3" size="small" border>
                <el-descriptions-item label="原始入库净重">
                  <b>{{ detail.review.originalNetWeight }} kg</b>
                </el-descriptions-item>
                <el-descriptions-item label="分拣投入">{{ detail.review.sortingInputWeight }} kg</el-descriptions-item>
                <el-descriptions-item label="分拣产出">{{ detail.review.sortingOutputWeight }} kg</el-descriptions-item>
                <el-descriptions-item label="登记损耗">
                  <span class="risk-critical">{{ detail.review.sortingLossWeight }} kg
                    （{{ (detail.review.sortingLossRate*100).toFixed(1) }}%）</span>
                </el-descriptions-item>
                <el-descriptions-item label="货主主张">
                  ≤ {{ detail.review.claimedLossRate != null ? (detail.review.claimedLossRate*100).toFixed(1)+'%' : '另行核定' }}
                </el-descriptions-item>
                <el-descriptions-item label="质检员签名">
                  <b>{{ detail.review.qcSignature }}</b>
                </el-descriptions-item>
                <el-descriptions-item label="分拣视频" :span="3">{{ detail.review.sortingVideo }}</el-descriptions-item>
                <el-descriptions-item label="坏果照片" :span="3">{{ detail.review.badFruitPhotos }}</el-descriptions-item>
                <el-descriptions-item label="等级划分" :span="3">{{ detail.review.gradeBreakdown }}</el-descriptions-item>
                <el-descriptions-item label="质检/货主说明" :span="3">
                  {{ detail.review.qcEvidenceNote }}｜{{ detail.review.claimReason }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <!-- 重新抽样 -->
            <el-card shadow="never" style="margin-bottom:12px">
              <div class="card-title">复核员重新抽样</div>
              <el-form :model="sampleForm" inline>
                <el-form-item label="抽样重量kg">
                  <el-input-number v-model="sampleForm.sampleWeight" :min="0" :precision="2" />
                </el-form-item>
                <el-form-item label="坏果重量kg">
                  <el-input-number v-model="sampleForm.sampleBadWeight" :min="0" :precision="2"
                                   :max="sampleForm.sampleWeight" />
                </el-form-item>
                <el-form-item label="抽样人">
                  <el-input v-model="sampleForm.sampler" placeholder="复核员姓名" style="width:130px" />
                </el-form-item>
                <el-form-item label="抽样发现">
                  <el-input v-model="sampleForm.sampleNote" style="width:280px"
                            placeholder="坏果部位/挤压/失水特征" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :disabled="readonly"
                             @click="submitSample">提交抽样（坏果率 {{ sampleBadPct.toFixed(1) }}%）</el-button>
                </el-form-item>
              </el-form>
              <el-alert v-if="detail.review.sampledAt" type="info" :closable="false" style="margin-top:4px"
                        :title="`已抽样 ${detail.review.sampleWeight}kg，坏果 ${detail.review.sampleBadWeight}kg，抽样坏果率 ${(detail.review.sampleBadRate*100).toFixed(1)}%（${fmt(detail.review.sampledAt)} ${detail.review.sampler}）${detail.review.sampleNote ? '：' + detail.review.sampleNote : ''}`" />
            </el-card>

            <!-- 周转筐线索 -->
            <el-card shadow="never" style="margin-bottom:12px">
              <div class="card-title">
                坏果周转筐串联分析（筐号 → 分拣工位 → 搬运时间）
                <el-button type="primary" size="small" style="float:right" :disabled="readonly"
                           @click="openBasket">登记筐号线索</el-button>
              </div>
              <el-table :data="detail.baskets" size="small">
                <el-table-column prop="basketNo" label="周转筐号" width="110" class="mono" />
                <el-table-column prop="workstation" label="分拣工位" width="120" />
                <el-table-column label="上台时间" width="150">
                  <template #default="{ row }">{{ fmt(row.sortStationAt) }}</template>
                </el-table-column>
                <el-table-column label="搬离时间" width="150">
                  <template #default="{ row }">{{ fmt(row.movedAwayAt) }}</template>
                </el-table-column>
                <el-table-column label="暴露min" width="80">
                  <template #default="{ row }">
                    <span :class="exposure(row) >= 30 ? 'risk-critical' : ''">{{ exposure(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="moveStage" label="搬运环节" width="100" />
                <el-table-column prop="mover" label="搬运人/班组" width="110" />
                <el-table-column label="筐内坏果率" width="95">
                  <template #default="{ row }">{{ (row.badRate*100).toFixed(1) }}%</template>
                </el-table-column>
                <el-table-column label="串联判定" width="150">
                  <template #default="{ row }">
                    <el-tag size="small" :type="causeType(row.cause)">{{ $dict('BasketCause', row.cause) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="causeBasis" label="判定依据" min-width="220" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!detail.baskets.length" description="尚未登记筐号线索" :image-size="50" />
            </el-card>

            <!-- 复核结论 -->
            <el-card shadow="never" style="margin-bottom:12px">
              <div class="card-title">复核结论与责任划分</div>
              <template v-if="!readonly">
                <el-form label-width="130px">
                  <el-form-item label="损耗成因结论">
                    <el-radio-group v-model="conclForm.conclusion">
                      <el-radio value="OWNER_QUALITY">到货质量差（货主承担）</el-radio>
                      <el-radio value="LOADING_SQUEEZE">装卸挤压（冷库赔付）</el-radio>
                      <el-radio value="SORT_EXPOSURE">分拣台长时间暴露（冷库赔付）</el-radio>
                      <el-radio value="MIXED">多方因素（分摊赔付）</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-row :gutter="10">
                    <el-col :span="8">
                      <el-form-item label="确认损耗kg">
                        <el-input-number v-model="conclForm.confirmedLossWeight" :min="0"
                                         :max="Number(detail.review.sortingLossWeight)" :precision="2" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8" v-if="conclForm.conclusion === 'MIXED'">
                      <el-form-item label="货主承担kg">
                        <el-input-number v-model="conclForm.ownerBorneWeight" :min="0" :precision="2"
                                         @change="syncMixed" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8" v-if="conclForm.conclusion === 'MIXED'">
                      <el-form-item label="冷库承担kg">
                        <el-input-number v-model="conclForm.warehouseBorneWeight" :min="0" :precision="2"
                                         @change="syncMixed" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="赔付单价元/kg">
                        <el-input-number v-model="conclForm.compensatePrice" :min="0" :precision="2" />
                      </el-form-item>
                    </el-col>
                  </el-row>
              <el-alert v-if="conclForm.conclusion === 'MIXED' && !mixedValid" type="warning" :closable="false"
                        style="margin-bottom:10px" title="MIXED 结论下：货主承担 + 冷库承担 必须等于确认损耗" />
              <el-descriptions :column="3" size="small" border style="margin-bottom:12px">
                <el-descriptions-item label="货主承担">
                  {{ ownerBorne }} kg（到货质量，不计价）
                </el-descriptions-item>
                <el-descriptions-item label="冷库承担">
                  {{ warehouseBorne }} kg
                </el-descriptions-item>
                <el-descriptions-item label="冷库赔付金额">
                  <b class="risk-critical">{{ warehouseComp }} 元</b>
                </el-descriptions-item>
                <el-descriptions-item label="可售重量回补">
                  {{ salableBack }} kg（= 登记损耗 - 确认损耗，回写分拣单与批次）
                </el-descriptions-item>
              </el-descriptions>

                  <el-form-item label="对客赔付">
                    <el-switch v-model="conclForm.customerCompNeeded"
                               active-text="坏果已流向客户，登记客户赔付记录" />
                  </el-form-item>
                  <template v-if="conclForm.customerCompNeeded">
                    <el-row :gutter="10">
                      <el-col :span="8">
                        <el-form-item label="受偿客户">
                          <el-select v-model="conclForm.customerId" filterable clearable placeholder="可后补"
                                     style="width:100%">
                            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
                          </el-select>
                        </el-form-item>
                      </el-col>
                      <el-col :span="8">
                        <el-form-item label="赔付金额元">
                          <el-input-number v-model="conclForm.customerCompAmount" :min="0" :precision="2" />
                        </el-form-item>
                      </el-col>
                      <el-col :span="8">
                        <el-form-item label="赔付说明">
                          <el-input v-model="conclForm.customerCompNote" />
                        </el-form-item>
                      </el-col>
                    </el-row>
                  </template>
                  <el-form-item label="复核人签名">
                    <el-input v-model="conclForm.reviewer" placeholder="复核员姓名" style="width:200px" />
                  </el-form-item>
                  <el-form-item label="综合意见">
                    <el-input v-model="conclForm.conclusionNote" type="textarea" :rows="2"
                              placeholder="结合视频、坏果照片、抽样坏果率与筐号线索给出依据" />
                  </el-form-item>
                  <el-button type="primary" :disabled="!canConclude" @click="submitConclude">
                    出具复核结论（回写结算单与考核）
                  </el-button>
                </el-form>
              </template>
              <template v-else>
                <el-descriptions :column="3" size="small" border>
                  <el-descriptions-item label="结论">
                    <el-tag :type="detail.review.conclusion.startsWith('OWNER') ? 'warning' : 'danger'">
                      {{ $dict('ReviewConclusion', detail.review.conclusion) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="确认损耗">{{ detail.review.confirmedLossWeight }} kg</el-descriptions-item>
                  <el-descriptions-item label="货主承担">{{ detail.review.ownerBorneWeight }} kg</el-descriptions-item>
                  <el-descriptions-item label="冷库承担">{{ detail.review.warehouseBorneWeight }} kg</el-descriptions-item>
                  <el-descriptions-item label="冷库赔付">{{ detail.review.compensateAmount }} 元
                    （{{ detail.review.compensatePrice }} 元/kg）</el-descriptions-item>
                  <el-descriptions-item label="可售回补">{{ detail.review.salableAdjustWeight }} kg</el-descriptions-item>
                  <el-descriptions-item label="复核人">{{ detail.review.reviewer }}
                    （{{ fmt(detail.review.confirmedAt) }}）</el-descriptions-item>
                  <el-descriptions-item label="结算回写">
                    <el-tag size="small" :type="detail.review.billId ? 'success' : 'info'">
                      {{ detail.review.billId ? '已回写账单 #' + detail.review.billId + '（重新生成账单即生效）' : '待生成账单' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="综合意见" :span="3">{{ detail.review.conclusionNote }}</el-descriptions-item>
                </el-descriptions>
              </template>
            </el-card>

            <!-- 考核 + 客户赔付 -->
            <el-row :gutter="12">
              <el-col :span="12">
                <el-card shadow="never">
                  <div class="card-title">考核回写（质检员 / 班组）</div>
                  <el-table :data="detail.assessments" size="small">
                    <el-table-column label="对象" width="100">
                      <template #default="{ row }">{{ $dict('AssessmentTarget', row.target) }}</template>
                    </el-table-column>
                    <el-table-column prop="targetName" label="姓名/班组" width="120" />
                    <el-table-column label="处理" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="row.result === 'DEDUCT' ? 'danger' : 'info'">
                          {{ $dict('AssessmentResult', row.result) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="scoreChange" label="扣分" width="60" />
                    <el-table-column prop="reason" label="事由" show-overflow-tooltip />
                  </el-table>
                  <el-empty v-if="!detail.assessments.length" description="出具结论后生成" :image-size="50" />
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never">
                  <div class="card-title">客户赔付记录</div>
                  <el-table :data="detail.compensations" size="small">
                    <el-table-column prop="compensationNo" label="赔付单号" width="150" class="mono" />
                    <el-table-column prop="amount" label="金额" width="80" />
                    <el-table-column label="状态" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="row.status === '已赔付' ? 'success' : 'warning'">{{ row.status }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="80">
                      <template #default="{ row }">
                        <el-button v-if="row.status !== '已赔付'" link type="primary"
                                   @click="payComp(row)">标记已赔</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-empty v-if="!detail.compensations.length" description="无对客赔付" :image-size="50" />
                </el-card>
              </el-col>
            </el-row>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ================= 考核台账 ================= -->
      <el-tab-pane label="质检员 / 班组考核台账" name="assess">
        <el-card shadow="never">
          <el-table :data="assessList" size="small">
            <el-table-column label="复核单号" width="165" class="mono">
              <template #default="{ row }">{{ reviewNoOf(row.reviewId) }}</template>
            </el-table-column>
            <el-table-column label="批次" width="160">
              <template #default="{ row }">{{ batchNo(row.batchId) }}</template>
            </el-table-column>
            <el-table-column label="考核对象" width="110">
              <template #default="{ row }">{{ $dict('AssessmentTarget', row.target) }}</template>
            </el-table-column>
            <el-table-column prop="targetName" label="姓名/班组" width="140" />
            <el-table-column label="处理结果" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 'DEDUCT' ? 'danger' : 'info'">
                  {{ $dict('AssessmentResult', row.result) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scoreChange" label="扣分" width="70" />
            <el-table-column prop="penaltyAmount" label="扣款元" width="80" />
            <el-table-column prop="reason" label="考核事由" min-width="260" show-overflow-tooltip />
            <el-table-column prop="operator" label="登记人" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ================= 客户赔付台账 ================= -->
      <el-tab-pane label="客户赔付记录台账" name="comp">
        <el-card shadow="never">
          <el-table :data="compList" size="small">
            <el-table-column prop="compensationNo" label="赔付单号" width="170" class="mono" />
            <el-table-column label="批次" width="160">
              <template #default="{ row }">{{ batchNo(row.batchId) }}</template>
            </el-table-column>
            <el-table-column label="客户" width="160">
              <template #default="{ row }">{{ customerName(row.customerId) }}</template>
            </el-table-column>
            <el-table-column prop="weight" label="坏果kg" width="90" />
            <el-table-column prop="amount" label="赔付元" width="90" />
            <el-table-column prop="reason" label="原因" min-width="240" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === '已赔付' ? 'success' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已赔付'" link type="primary" @click="payComp(row)">赔付到账</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 发起复核对话框 -->
    <el-dialog v-model="applyVisible" title="货主发起分拣损耗争议复核" width="620px">
      <el-form label-width="110px">
        <el-form-item label="选择批次">
          <el-select v-model="applyForm.batchId" filterable placeholder="选择已分拣批次" style="width:100%"
                     @change="onApplyBatch">
            <el-option v-for="b in batches" :key="b.id"
                       :label="`${b.batchNo} ${catName(b.categoryId)}（${$dict('BatchStatus', b.status)}）`"
                       :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分拣作业单">
          <el-select v-model="applyForm.sortingId" placeholder="选择分拣单" style="width:100%">
            <el-option v-for="s in applySortings" :key="s.id"
                       :label="`${s.sortingNo} 投入${s.inputWeight}kg 损耗${s.lossWeight}kg（${(s.lossRate*100).toFixed(1)}%）`"
                       :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="applyForm.applicant" placeholder="货主/登记人" />
        </el-form-item>
        <el-form-item label="主张损耗率%">
          <el-input-number v-model="applyClaimedPct" :min="0" :max="100" :precision="1" />
          <span class="hint">平台将固定：原重、分拣视频、坏果照片、等级划分、质检员签名</span>
        </el-form-item>
        <el-form-item label="争议说明">
          <el-input v-model="applyForm.claimReason" type="textarea" :rows="2"
                    placeholder="货主认为损耗比例过高的理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交并固定证据</el-button>
      </template>
    </el-dialog>

    <!-- 筐号线索对话框 -->
    <el-dialog v-model="basketVisible" title="登记坏果周转筐线索" width="620px">
      <el-form label-width="110px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="周转筐号">
              <el-input v-model="basketForm.basketNo" placeholder="如 K-0231" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分拣工位">
              <el-input v-model="basketForm.workstation" placeholder="如 A区3号台" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上台时间">
              <el-date-picker v-model="basketForm.sortStationAt" type="datetime" style="width:100%"
                              value-format="YYYY-MM-DD HH:mm:ss" placeholder="筐到分拣台时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="搬离时间">
              <el-date-picker v-model="basketForm.movedAwayAt" type="datetime" style="width:100%"
                              value-format="YYYY-MM-DD HH:mm:ss" placeholder="筐离开台面时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="搬运环节">
              <el-select v-model="basketForm.moveStage" style="width:100%">
                <el-option label="到货卸车" value="到货卸车" />
                <el-option label="上台分拣" value="上台分拣" />
                <el-option label="入库搬运" value="入库搬运" />
                <el-option label="装车出库" value="装车出库" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="搬运人/班组">
              <el-input v-model="basketForm.mover" placeholder="如 装卸二班" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="筐内坏果率%">
              <el-input-number v-model="basketBadPct" :min="0" :max="100" :precision="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因判定">
              <el-select v-model="basketForm.cause" clearable placeholder="留空按时间自动判定" style="width:100%">
                <el-option label="来货质量差" value="ARRIVAL_QUALITY" />
                <el-option label="装卸挤压" value="LOADING_SQUEEZE" />
                <el-option label="分拣台长时间暴露" value="SORT_EXPOSURE" />
                <el-option label="混合原因" value="INCLUSIVE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="判定依据备注">
          <el-input v-model="basketForm.causeBasis" type="textarea" :rows="2"
                    placeholder="挤压痕迹/失水萎蔫/同批对比；暴露时长系统自动计算" />
        </el-form-item>
        <el-alert v-if="basketExposure >= 0" :type="basketExposure >= 30 ? 'error' : 'success'"
                  :closable="false"
                  :title="`台面暴露 ${basketExposure} 分钟${basketExposure >= 30 ? ' ≥ 30 分钟，倾向判定分拣台长时间暴露' : '，时间正常'}`" />
      </el-form>
      <template #footer>
        <el-button @click="basketVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBasket">保存线索</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const route = useRoute()
const tab = ref('work')
const scope = ref('open')
const list = ref([])
const detail = ref(null)
const currentId = ref(null)
const batches = ref([])
const categories = ref([])
const customers = ref([])
const assessList = ref([])
const compList = ref([])

const readonly = computed(() => detail.value && detail.review.status === 'CONFIRMED')

const sampleForm = reactive({ sampleWeight: 0, sampleBadWeight: 0, sampler: '', sampleNote: '' })
const sampleBadPct = computed(() => sampleForm.sampleWeight > 0
  ? sampleForm.sampleBadWeight / sampleForm.sampleWeight * 100 : 0)

const conclForm = reactive({
  conclusion: 'LOADING_SQUEEZE', confirmedLossWeight: 0, ownerBorneWeight: null,
  warehouseBorneWeight: null, compensatePrice: 0,
  customerCompNeeded: false, customerId: null, customerCompAmount: 0, customerCompNote: '',
  reviewer: '', conclusionNote: ''
})
const ownerBorne = computed(() => {
  if (conclForm.conclusion === 'OWNER_QUALITY') return Number(conclForm.confirmedLossWeight || 0)
  if (conclForm.conclusion === 'MIXED') return Number(conclForm.ownerBorneWeight || 0)
  return 0
})
const warehouseBorne = computed(() => {
  if (conclForm.conclusion === 'MIXED') return Number(conclForm.warehouseBorneWeight || 0)
  if (conclForm.conclusion === 'OWNER_QUALITY') return 0
  return Number(conclForm.confirmedLossWeight || 0)
})
const warehouseComp = computed(() =>
  (warehouseBorne.value * Number(conclForm.compensatePrice || 0)).toFixed(2))
const salableBack = computed(() => {
  const total = Number(detail.value?.review.sortingLossWeight || 0)
  return Math.max(0, total - Number(conclForm.confirmedLossWeight || 0))
})
const mixedValid = computed(() =>
  Math.abs(ownerBorne.value + warehouseBorne.value
    - Number(conclForm.confirmedLossWeight || 0)) < 0.001)
const canConclude = computed(() =>
  !!conclForm.reviewer && Number(conclForm.confirmedLossWeight) >= 0
  && (conclForm.conclusion !== 'MIXED' || mixedValid.value))

function syncMixed() { /* 响应式自动核算，占位便于扩展 */ }

// ---------- 发起复核 ----------
const applyVisible = ref(false)
const applySortings = ref([])
const applyClaimedPct = ref(3)
const applyForm = reactive({
  batchId: null, sortingId: null, applicant: '', claimedLossRate: 0.03, claimReason: ''
})

async function openApply() {
  applyForm.batchId = null
  applyForm.sortingId = null
  applyForm.applicant = ''
  applyForm.claimReason = ''
  applyClaimedPct.value = 3
  applySortings.value = []
  applyVisible.value = true
  const preSorting = Number(route.query.sortingId)
  const preBatch = Number(route.query.batchId)
  if (preSorting && preBatch) {
    applyForm.batchId = preBatch
    await onApplyBatch(preBatch)
    applyForm.sortingId = preSorting
  }
}

async function onApplyBatch(bid) {
  applyForm.sortingId = null
  applySortings.value = bid ? await api.get('/api/sortings/batch/' + bid) : []
}

async function submitApply() {
  if (!applyForm.sortingId) {
    ElMessage.warning('请选择分拣作业单')
    return
  }
  const rv = await api.post('/api/loss-reviews/apply', {
    sortingId: applyForm.sortingId,
    applicant: applyForm.applicant || '货主',
    claimedLossRate: Number((applyClaimedPct.value / 100).toFixed(4)),
    claimReason: applyForm.claimReason
  })
  ElMessage.success('复核单已建立，证据快照已固定')
  applyVisible.value = false
  tab.value = 'work'
  scope.value = 'open'
  await loadList()
  await selectReview(rv.id)
}

// ---------- 筐号线索 ----------
const basketVisible = ref(false)
const basketBadPct = ref(0)
const basketForm = reactive({
  basketNo: '', workstation: '', sortStationAt: null, movedAwayAt: null,
  mover: '', moveStage: '上台分拣', badRate: 0, cause: '', causeBasis: ''
})
const basketExposure = computed(() => exposure({
  sortStationAt: basketForm.sortStationAt, movedAwayAt: basketForm.movedAwayAt
}))

function openBasket() {
  Object.assign(basketForm, {
    basketNo: '', workstation: '', sortStationAt: null, movedAwayAt: null,
    mover: '', moveStage: '上台分拣', cause: '', causeBasis: ''
  })
  basketBadPct.value = 0
  basketVisible.value = true
}

async function submitBasket() {
  if (!basketForm.basketNo) {
    ElMessage.warning('请填写周转筐号')
    return
  }
  await api.post(`/api/loss-reviews/${currentId.value}/baskets`, {
    ...basketForm,
    badRate: Number((basketBadPct.value / 100).toFixed(4))
  })
  ElMessage.success('筐号线索已登记并完成串联判定')
  basketVisible.value = false
  await loadDetail()
}

// ---------- 抽样 / 结论 ----------
async function submitSample() {
  if (!(sampleForm.sampleWeight > 0)) {
    ElMessage.warning('请填写抽样重量')
    return
  }
  await api.post(`/api/loss-reviews/${currentId.value}/sample`, {
    ...sampleForm,
    sampler: sampleForm.sampler || '复核员'
  })
  ElMessage.success('抽样结果已记录')
  await loadDetail()
}

async function submitConclude() {
  await ElMessageBox.confirm('出具结论后将回写分拣单/批次可售重量、结算账单口径与质检员班组考核，确认提交？',
    '复核结论确认', { type: 'warning' })
  const payload = {
    conclusion: conclForm.conclusion,
    confirmedLossWeight: conclForm.confirmedLossWeight,
    compensatePrice: conclForm.compensatePrice,
    customerCompNeeded: conclForm.customerCompNeeded,
    customerId: conclForm.customerId,
    customerCompAmount: conclForm.customerCompAmount,
    customerCompNote: conclForm.customerCompNote,
    reviewer: conclForm.reviewer,
    conclusionNote: conclForm.conclusionNote
  }
  if (conclForm.conclusion === 'MIXED') {
    payload.ownerBorneWeight = conclForm.ownerBorneWeight
    payload.warehouseBorneWeight = conclForm.warehouseBorneWeight
  }
  await api.post(`/api/loss-reviews/${currentId.value}/conclude`, payload)
  ElMessage.success('复核结论已出具，账单/考核/赔付已回写')
  await loadList()
  await loadDetail()
}

async function payComp(row) {
  await api.post(`/api/loss-reviews/compensations/${row.id}/pay?operator=${encodeURIComponent('财务')}`)
  ElMessage.success('已标记赔付到账')
  await loadDetail()
  loadLedgers()
}

// ---------- 加载 ----------
async function loadList() {
  list.value = await api.get('/api/loss-reviews' + (scope.value === 'open' ? '?scope=open' : ''))
}

async function onSelect(row) {
  if (!row || !row.id) return
  await selectReview(row.id)
}

async function selectReview(id) {
  currentId.value = id
  await loadDetail()
}

async function loadDetail() {
  if (!currentId.value) {
    detail.value = null
    return
  }
  detail.value = await api.get('/api/loss-reviews/' + currentId.value)
  const rv = detail.value.review
  if (rv.status !== 'CONFIRMED') {
    conclForm.conclusion = 'LOADING_SQUEEZE'
    conclForm.confirmedLossWeight = Number(rv.sortingLossWeight)
    conclForm.ownerBorneWeight = null
    conclForm.warehouseBorneWeight = null
    conclForm.compensatePrice = avgSortPrice(detail.value)
    conclForm.customerCompNeeded = false
    conclForm.customerCompAmount = 0
    conclForm.customerCompNote = ''
    if (rv.sampleWeight) {
      sampleForm.sampleWeight = Number(rv.sampleWeight)
      sampleForm.sampleBadWeight = Number(rv.sampleBadWeight)
      sampleForm.sampler = rv.sampler
      sampleForm.sampleNote = rv.sampleNote
    }
  }
}

function avgSortPrice(d) {
  const items = d.sortItems || []
  const w = items.reduce((s, i) => s + Number(i.weight || 0), 0)
  const v = items.reduce((s, i) => s + Number(i.weight || 0) * Number(i.unitPrice || 0), 0)
  return w > 0 ? Number((v / w).toFixed(2)) : 0
}

async function loadLedgers() {
  const [a, c] = await Promise.all([
    api.get('/api/loss-reviews/assessments'),
    api.get('/api/loss-reviews/compensations')
  ])
  assessList.value = a
  compList.value = c
}

function batchNo(id) { return batches.value.find(b => b.id === id)?.batchNo || '#' + id }
function catName(id) { return categories.value.find(c => c.id === id)?.name || id }
function customerName(id) { return customers.value.find(c => c.id === id)?.name || (id ? '#' + id : '待联系客户') }
function reviewNoOf(id) { return list.value.find(r => r.id === id)?.reviewNo || '#' + id }
function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '-' }
function exposure(row) {
  if (!row.sortStationAt || !row.movedAwayAt) return 0
  const a = new Date(row.sortStationAt.replace(' ', 'T'))
  const b = new Date(row.movedAwayAt.replace(' ', 'T'))
  return Math.max(0, Math.round((b - a) / 60000))
}
function statusType(s) {
  return { OPEN: 'danger', SAMPLING: 'warning', CONFIRMED: 'success', CANCELLED: 'info' }[s] || 'info'
}
function causeType(c) {
  return { ARRIVAL_QUALITY: 'warning', LOADING_SQUEEZE: 'danger',
    SORT_EXPOSURE: 'danger', INCLUSIVE: 'warning' }[c] || 'info'
}

onMounted(async () => {
  const [b, c, cu] = await Promise.all([
    api.get('/api/batches'),
    api.get('/api/masters/categories'),
    api.get('/api/masters/customers')
  ])
  batches.value = b
  categories.value = c
  customers.value = cu
  await loadList()
  await loadLedgers()
  if (list.value.length) await selectReview(list.value[0].id)
  if (route.query.sortingId) openApply()
})
</script>

<style scoped>
.review-tabs { background: transparent; }
.hint { color: #909daa; font-size: 12px; margin-left: 8px; }
:deep(.sel > td) { background: #e8f4ef !important; }
</style>
