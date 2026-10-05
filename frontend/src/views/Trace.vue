<template>
  <div class="page">
    <el-card shadow="never" style="margin-bottom:12px">
      <div style="display:flex;gap:10px;align-items:center">
        <span class="card-title" style="margin:0">监管追溯查询</span>
        <el-select v-model="batchId" filterable placeholder="选择批次" style="width:340px" @change="load">
          <el-option v-for="b in batches" :key="b.id"
                     :label="`${b.batchNo} ${catName(b.categoryId)}（${$dict('BatchStatus', b.status)}）`"
                     :value="b.id" />
        </el-select>
        <el-tag v-if="data" size="small">
          追溯节点 {{ data.timeline.length }} 个 · 最终客户流向 {{ data.finalFlow.length }} 条
        </el-tag>
      </div>
    </el-card>

    <template v-if="data">
      <el-card shadow="never" style="margin-bottom:12px">
        <div class="card-title">批次全景（从入库到最终客户）</div>
        <el-descriptions :column="4" size="small" border>
          <el-descriptions-item label="批次号">{{ data.batch.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="货主">{{ data.batch.owner }}</el-descriptions-item>
          <el-descriptions-item label="品类/产地">{{ data.batch.category }} / {{ data.batch.origin }}</el-descriptions-item>
          <el-descriptions-item label="车辆">{{ data.batch.vehicleNo }}
            <el-tag v-if="data.batch.mixedLoad" type="warning" size="small">混装</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="净重/到货温度">{{ data.batch.netWeight }}kg / {{ data.batch.arrivalTemp }}℃</el-descriptions-item>
          <el-descriptions-item label="检疫证">
            {{ data.batch.quarantine?.certificateNo }}
            <el-tag size="small" :type="data.batch.quarantine?.verified ? 'success' : 'danger'">
              {{ data.batch.quarantine?.verified ? '已核验' : '未核验' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ $dict('SettleMethod', data.batch.settleMethod) }}</el-descriptions-item>
          <el-descriptions-item label="计划客户">{{ data.batch.plannedCustomer || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-steps direction="vertical" :active="data.timeline.length" style="margin-top:16px">
          <el-step v-for="e in data.timeline" :key="e.id"
                   :title="`${fmt(e.eventAt)} 【${$dict('Stage', e.stage)}】${e.title}`"
                   :description="e.detail" :status="stepStatus(e.stage)" />
        </el-steps>
      </el-card>

      <el-row :gutter="12">
        <el-col :span="24">
          <el-card shadow="never" style="margin-bottom:12px">
            <div class="card-title">分拣损耗争议复核（证据 / 抽样 / 筐号串联 / 结论 / 考核 / 客户赔付）</div>
            <el-empty v-if="!data.lossReviews.length" description="无损耗复核" :image-size="50" />
            <template v-for="g in data.lossReviews" :key="g.review.id">
              <el-descriptions :column="2" size="small" border style="margin-bottom:8px">
                <el-descriptions-item label="复核单号">{{ g.review.reviewNo }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ $dict('ReviewStatus', g.review.status) }}</el-descriptions-item>
                <el-descriptions-item label="原重/登记损耗">
                  {{ g.review.originalNetWeight }}kg / {{ g.review.sortingLossWeight }}kg
                </el-descriptions-item>
                <el-descriptions-item label="抽样坏果率">
                  {{ g.review.sampleBadRate ? (g.review.sampleBadRate*100).toFixed(1)+'%' : '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="结论" :span="2">
                  <el-tag v-if="g.review.conclusion" size="small"
                          :type="g.review.conclusion.startsWith('OWNER') ? 'warning' : 'danger'">
                    {{ $dict('ReviewConclusion', g.review.conclusion) }}
                  </el-tag>
                  <span v-else>待出具结论</span>
                </el-descriptions-item>
                <el-descriptions-item v-if="g.review.conclusion" label="货主/冷库承担">
                  {{ g.review.ownerBorneWeight }}kg / {{ g.review.warehouseBorneWeight }}kg
                </el-descriptions-item>
                <el-descriptions-item v-if="g.review.conclusion" label="冷库赔付">
                  {{ g.review.compensateAmount }} 元
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="g.baskets.length" class="hint" style="margin-bottom:6px">
                坏果筐：<el-tag v-for="b in g.baskets" :key="b.id" size="small"
                  :type="b.cause === 'ARRIVAL_QUALITY' ? 'warning' : 'danger'" style="margin-right:4px">
                  {{ b.basketNo }}·{{ b.workstation }}·{{ $dict('BasketCause', b.cause) }}
                </el-tag>
              </div>
              <div v-if="g.assessments.length" class="hint" style="margin-bottom:6px">
                考核：<span v-for="(a, i) in g.assessments" :key="a.id">
                  {{ $dict('AssessmentTarget', a.target) }}{{ a.targetName }}
                  {{ $dict('AssessmentResult', a.result) }}{{ a.scoreChange }}分<span v-if="i < g.assessments.length-1">；</span>
                </span>
              </div>
              <div v-if="g.compensations.length" class="hint" style="margin-bottom:10px">
                客户赔付：<span v-for="p in g.compensations" :key="p.id">
                  {{ p.compensationNo }} {{ p.amount }}元（{{ p.status }}）
                </span>
              </div>
            </template>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-card shadow="never" style="margin-bottom:12px">
            <div class="card-title">最终客户流向</div>
            <el-table :data="data.finalFlow" size="small">
              <el-table-column prop="orderNo" label="出库单" width="175" class="mono" />
              <el-table-column prop="customer" label="客户" width="150" />
              <el-table-column prop="weight" label="重量kg" width="80" />
              <el-table-column prop="amount" label="金额" width="90" />
              <el-table-column label="投诉">
                <template #default="{ row }">
                  <el-tag v-for="c in row.complaints" :key="c.id" type="danger" size="small">
                    {{ $dict('ComplaintType', c.type) }}/{{ $dict('Responsibility', c.responsibility) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" style="margin-bottom:12px">
            <div class="card-title">质检 / 账单结论</div>
            <el-table :data="data.inspections" size="small">
              <el-table-column label="结论" width="90">
                <template #default="{ row }">
                  <el-tag size="small">{{ $dict('Decision', row.decision) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="腐损率" width="80">
                <template #default="{ row }">{{ (row.rotRate*100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="inspector" label="质检员" width="90" />
              <el-table-column prop="remark" label="备注" />
            </el-table>
            <el-divider />
            <el-table :data="data.bills" size="small">
              <el-table-column prop="billNo" label="账单号" width="180" class="mono" />
              <el-table-column prop="netPayable" label="应付" width="90" />
              <el-table-column prop="paidAmount" label="已付" width="90" />
              <el-table-column label="状态">
                <template #default="{ row }">{{ $dict('BillStatus', row.status) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const batches = ref([])
const categories = ref([])
const batchId = ref(null)
const data = ref(null)

function catName(id) { return categories.value.find(c => c.id === id)?.name || id }
function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
function stepStatus(stage) {
  if (['LOSS', 'COMPLAINT'].includes(stage)) return 'error'
  return 'finish'
}

async function load() {
  if (!batchId.value) {
    data.value = null
    return
  }
  data.value = await api.get('/api/trace/batch/' + batchId.value)
}

onMounted(async () => {
  const [b, c] = await Promise.all([
    api.get('/api/batches'),
    api.get('/api/masters/categories')
  ])
  batches.value = b
  categories.value = c
  if (b.length) {
    batchId.value = b[0].id
    load()
  }
})
</script>
