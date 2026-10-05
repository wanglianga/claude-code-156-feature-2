<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="14">
        <el-card shadow="never">
          <div class="card-title">待检批次工作台</div>
          <el-table :data="pending" size="small" highlight-current-row
                    @current-change="onSelect" :row-class-name="r => r.id === form.batchId ? 'sel' : ''">
            <el-table-column prop="batchNo" label="批次号" width="170" class="mono" />
            <el-table-column label="品类" width="120">
              <template #default="{ row }">{{ catName(row.categoryId) }}</template>
            </el-table-column>
            <el-table-column prop="netWeight" label="净重kg" width="80" />
            <el-table-column label="到货温度" width="90">
              <template #default="{ row }">
                <span :class="tempBad(row) ? 'risk-critical' : ''">{{ row.arrivalTemp }}℃</span>
              </template>
            </el-table-column>
            <el-table-column label="票证" width="70">
              <template #default="{ row }">
                <el-tag size="small" :type="row.quarantine?.verified ? 'success' : 'danger'">
                  {{ row.quarantine?.verified ? '齐' : '缺' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="混装" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.mixedLoad" size="small" type="warning">混</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-top:12px" v-if="current">
          <div class="card-title">
            质检验收单 — {{ current.batchNo }}
            <el-tag size="small">允许到货温度 {{ currentCat?.minArrivalTemp }}~{{ currentCat?.maxArrivalTemp }}℃</el-tag>
          </div>
          <el-form :model="form" label-width="110px">
            <el-row :gutter="10">
              <el-col :span="8">
                <el-form-item label="质检员">
                  <el-input v-model="form.inspector" placeholder="姓名" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="外观评分">
                  <el-rate v-model="form.appearanceScore" :max="5" :colors="['#99a9bf','#e6a23c','#1f6f54']" />
                  <span class="hint">（×20={{ (form.appearanceScore||0)*20 }}分）</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="成熟度评分">
                  <el-rate v-model="form.maturityScore" :max="5" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="8">
                <el-form-item label="腐损率%">
                  <el-input-number v-model="rotPct" :min="0" :max="100" :precision="1" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="含水率%">
                  <el-input-number v-model="moisturePct" :min="0" :max="100" :precision="1" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="复测温度℃">
                  <el-input-number v-model="form.measuredTemp" :precision="1" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="票证核验">
                  <el-switch v-model="form.certificateOk" active-text="票证齐全真实" inactive-text="票证异常" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="混装已拆分">
                  <el-switch v-model="form.mixedConfirmed" active-text="已按批次拆分清楚" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">验收结论</el-divider>
            <el-radio-group v-model="form.decision" size="default">
              <el-radio-button value="ACCEPT">准予入库</el-radio-button>
              <el-radio-button value="SORT">转分拣</el-radio-button>
              <el-radio-button value="DOWNGRADE">降级处理</el-radio-button>
              <el-radio-button value="RETURN">退货</el-radio-button>
            </el-radio-group>

            <div v-if="form.decision === 'ACCEPT'" class="decision-box">
              <el-form-item label="入库等级" style="margin-top:12px">
                <el-radio-group v-model="form.suggestedGrade">
                  <el-radio value="A">A级精品</el-radio>
                  <el-radio value="B">B级通货</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="核定损耗率%">
                <el-input-number v-model="lossPct" :min="0" :max="100" :precision="1" style="width:200px" />
                <span class="hint" v-if="currentCat">
                  约定损耗率 {{ (currentCat.agreedLossRate*100).toFixed(1) }}%，
                  <b :class="lossPct > currentCat.agreedLossRate*100 ? 'risk-warn' : 'ok-text'">
                    {{ lossPct > currentCat.agreedLossRate*100 ? '超出将触发争议预警' : '在约定范围内' }}
                  </b>
                </span>
              </el-form-item>
            </div>

            <div v-if="form.decision === 'DOWNGRADE'" class="decision-box">
              <el-row :gutter="10" style="margin-top:12px">
                <el-col :span="8">
                  <el-form-item label="降级等级">
                    <el-select v-model="form.suggestedGrade">
                      <el-option label="B级通货" value="B" />
                      <el-option label="加工级" value="PROCESS" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="降级重量kg">
                    <el-input-number v-model="form.downgradeWeight" :min="0" :precision="2" style="width:100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="退货重量kg">
                    <el-input-number v-model="form.returnWeight" :min="0" :precision="2" style="width:100%" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <div v-if="form.decision === 'RETURN'" class="decision-box">
              <el-form-item label="退货重量kg" style="margin-top:12px">
                <el-input-number v-model="form.returnWeight" :min="0" :max="current.netWeight"
                                :precision="2" style="width:200px" />
                <el-button link @click="form.returnWeight = current.netWeight">整批退货</el-button>
              </el-form-item>
            </div>

            <el-form-item label="备注" style="margin-top:10px">
              <el-input v-model="form.remark" type="textarea" :rows="2"
                        placeholder="腐损部位、成熟度问题、温度复测说明等" />
            </el-form-item>
            <el-form-item label="质检员签名">
              <el-input v-model="form.signature" :placeholder="form.inspector || '电子签名（默认取质检员姓名）'"
                        style="max-width:320px" />
            </el-form-item>
            <el-button type="primary" :loading="saving" @click="submit">提交质检结论</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never">
          <div class="card-title">验收标准速查</div>
          <el-table :data="categories" size="small">
            <el-table-column prop="name" label="品类" width="150" />
            <el-table-column label="温度区间℃" width="110">
              <template #default="{ row }">{{ row.minArrivalTemp }}~{{ row.maxArrivalTemp }}</template>
            </el-table-column>
            <el-table-column label="建议温区">
              <template #default="{ row }">{{ $dict('ZoneType', row.recommendedZone) }}</template>
            </el-table-column>
            <el-table-column label="约定损耗" width="80">
              <template #default="{ row }">{{ (row.agreedLossRate*100).toFixed(0) }}%</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" style="margin-top:12px">
          <div class="card-title">本批次已检记录</div>
          <el-timeline v-if="history.length">
            <el-timeline-item v-for="h in history" :key="h.id" :timestamp="fmt(h.inspectedAt)" placement="top">
              <el-tag size="small">{{ $dict('Decision', h.decision) }}</el-tag>
              <span style="margin-left:8px">{{ h.inspector }}</span>
              <div class="hint">
                外观{{ h.appearanceScore }} 成熟度{{ h.maturityScore }}
                腐损{{ (h.rotRate*100).toFixed(1) }}% 温度{{ h.measuredTemp }}℃
              </div>
              <div class="hint" v-if="h.remark">备注：{{ h.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="选择左侧待检批次" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const route = useRoute()
const pending = ref([])
const categories = ref([])
const history = ref([])
const saving = ref(false)
const rotPct = ref(2)
const moisturePct = ref(90)
const lossPct = ref(2)

const defaultForm = () => ({
  batchId: null, inspector: '', appearanceScore: 4, maturityScore: 4,
  rotRate: 0.02, moistureRate: 0.9, measuredTemp: 5,
  certificateOk: true, mixedConfirmed: false,
  decision: 'SORT', suggestedGrade: 'A',
  downgradeWeight: 0, returnWeight: 0, estimatedLossRate: 0.02, signature: '', remark: ''
})
const form = reactive(defaultForm())

const current = computed(() => pending.value.find(b => b.id === form.batchId))
const currentCat = computed(() => categories.value.find(c => c.id === current.value?.categoryId))

watch(rotPct, v => form.rotRate = Number((v / 100).toFixed(4)))
watch(moisturePct, v => form.moistureRate = Number((v / 100).toFixed(4)))
watch(lossPct, v => form.estimatedLossRate = Number((v / 100).toFixed(4)))

function catName(id) {
  return categories.value.find(c => c.id === id)?.name || id
}
function tempBad(row) {
  const cat = categories.value.find(c => c.id === row.categoryId)
  if (!cat) return false
  return Number(row.arrivalTemp) > Number(cat.maxArrivalTemp)
      || Number(row.arrivalTemp) < Number(cat.minArrivalTemp)
}
function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

async function onSelect(row) {
  if (!row) return
  Object.assign(form, defaultForm(), { batchId: row.id, measuredTemp: Number(row.arrivalTemp) })
  rotPct.value = 2
  moisturePct.value = 90
  lossPct.value = 2
  history.value = await api.get('/api/inspections/batch/' + row.id)
}

async function submit() {
  if (!form.batchId) {
    ElMessage.warning('请先选择待检批次')
    return
  }
  if (!form.inspector) {
    ElMessage.warning('请填写质检员姓名')
    return
  }
  saving.value = true
  try {
    await api.post('/api/inspections', {
      ...form,
      appearanceScore: (form.appearanceScore || 0) * 20,
      maturityScore: (form.maturityScore || 0) * 20
    })
    ElMessage.success('质检结论已提交')
    await load()
    const keep = form.batchId
    const row = pending.value.find(b => b.id === keep)
    if (row) await onSelect(row)
  } finally {
    saving.value = false
  }
}

async function load() {
  pending.value = await api.get('/api/batches?status=REGISTERED')
  const targetId = Number(route.query.batchId) || pending.value[0]?.id
  if (targetId) {
    const row = pending.value.find(b => b.id === targetId)
    if (row) await onSelect(row)
  }
}

onMounted(async () => {
  categories.value = await api.get('/api/masters/categories')
  await load()
})
</script>

<style scoped>
.hint { color: #909daa; font-size: 12px; margin-left: 8px; }
.decision-box { background: #f7f9fa; border-radius: 8px; padding: 8px 12px; }
:deep(.sel > td) { background: #e8f4ef !important; }
</style>
