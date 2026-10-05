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

      <el-card v-if="data.lossReviews && data.lossReviews.length" shadow="never" style="margin-bottom:12px">
        <div class="card-title">分拣损耗争议复核结论</div>
        <el-table :data="data.lossReviews" size="small" border style="margin-bottom:10px">
          <el-table-column prop="reviewNo" label="复核单" width="160" class="mono" />
          <el-table-column label="成因" width="130">
            <template #default="{ row }">{{ $dict('LossCause', row.lossCause) }}</template>
          </el-table-column>
          <el-table-column label="责任" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.responsibility === 'WAREHOUSE' ? 'danger' : 'info'">
                {{ $dict('Responsibility', row.responsibility) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reviewedLossWeight" label="货主承担kg" width="95" />
          <el-table-column prop="warehouseBorneWeight" label="冷库承担kg" width="95" />
          <el-table-column prop="compensateAmount" label="冷库赔付元" width="90" />
          <el-table-column label="质检员考核" width="150">
            <template #default="{ row }">
              {{ row.inspectorName }}：
              <el-tag size="small" :type="row.inspectorDeductedPoints > 0 ? 'danger' : 'success'">
                {{ row.inspectorDeductedPoints > 0 ? '扣'+row.inspectorDeductedPoints+'分' : '不扣分' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="conclusion" label="结论" min-width="200" show-overflow-tooltip />
        </el-table>
        <div v-if="data.crateEvidences && data.crateEvidences.length" class="card-title" style="font-size:13px">
          周转筐证据链（筐号 ↔ 分拣工位 ↔ 搬运时间）
        </div>
        <el-table v-if="data.crateEvidences && data.crateEvidences.length" :data="data.crateEvidences"
                  size="small" border>
          <el-table-column prop="crateNo" label="筐号" width="80" />
          <el-table-column prop="sortStation" label="分拣工位" width="110" />
          <el-table-column prop="badFruitWeight" label="坏果kg" width="80" />
          <el-table-column label="判定成因" width="130">
            <template #default="{ row }">{{ $dict('LossCause', row.indicatedCause) }}</template>
          </el-table-column>
          <el-table-column label="暴露时长" width="90">
            <template #default="{ row }">{{ row.exposedMinutes != null ? row.exposedMinutes+' 分钟' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="damagePattern" label="坏果形态" min-width="150" />
        </el-table>
      </el-card>
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
