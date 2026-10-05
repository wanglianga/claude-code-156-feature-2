<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="card-title">待分拣批次</div>
          <el-table :data="sortingBatches" size="small" highlight-current-row
                    @current-change="onSelect">
            <el-table-column prop="batchNo" label="批次号" width="160" class="mono" />
            <el-table-column label="品类" width="100">
              <template #default="{ row }">{{ catName(row.categoryId) }}</template>
            </el-table-column>
            <el-table-column label="剩余可处理" width="100">
              <template #default="{ row }">
                <span class="risk-warn">{{ remaining(row) }}</span> kg
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-top:12px">
          <div class="card-title">分拣损耗率警戒线</div>
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label="预警线">≥ 5%（黄色告警）</el-descriptions-item>
            <el-descriptions-item label="红线">≥ 10%（严重告警）</el-descriptions-item>
            <el-descriptions-item label="货主争议">报损暂挂，管理端裁定后入账单</el-descriptions-item>
            <el-descriptions-item label="工时费">0.6 元/分钟（低温仓位作业）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never" v-if="current">
          <div class="card-title">
            分拣作业 — {{ current.batchNo }}（{{ catName(current.categoryId) }}）
          </div>
          <el-form :model="form" label-width="100px">
            <el-row :gutter="10">
              <el-col :span="8">
                <el-form-item label="分拣员">
                  <el-input v-model="form.sorter" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="投入重量kg">
                  <el-input-number v-model="form.inputWeight" :min="0" :precision="2" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工时(分钟)">
                  <el-input-number v-model="form.laborMinutes" :min="0" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="8">
                <el-form-item label="分拣工位">
                  <el-input v-model="form.sortStation" placeholder="如 1号分拣台" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="分拣视频">
                  <el-input v-model="form.sortVideoUrl" placeholder="视频URL/编号" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="坏果照片">
                  <el-input v-model="form.badFruitPhotos" placeholder="URL/编号，逗号分隔" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-table :data="form.items" size="small" border>
              <el-table-column label="等级" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.grade" size="small">
                    <el-option label="A级精品" value="A" />
                    <el-option label="B级通货" value="B" />
                    <el-option label="加工级" value="PROCESS" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="重量kg" width="150">
                <template #default="{ row }">
                  <el-input-number v-model="row.weight" :min="0" :precision="2" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="售价元/kg" width="150">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="保鲜天数" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.shelfLifeDays" :min="1" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="目标温区">
                <template #default="{ row }">
                  <el-select v-model="row.zoneCode" size="small">
                    <el-option label="冰温保鲜区" value="CHILLED" />
                    <el-option label="冷藏区" value="REFRIGERATED" />
                    <el-option label="冷冻区" value="FROZEN" />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>

            <div class="sum-row">
              <div>投入：<b>{{ form.inputWeight }}</b> kg</div>
              <div>产出合计：<b class="ok-text">{{ outputTotal }}</b> kg</div>
              <div>损耗：<b :class="lossRate >= 0.1 ? 'risk-critical' : lossRate >= 0.05 ? 'risk-warn' : 'ok-text'">
                {{ lossWeight }} kg（{{ (lossRate * 100).toFixed(1) }}%）
              </b></div>
              <div>工时费：{{ (form.laborMinutes * 0.6).toFixed(2) }} 元</div>
            </div>

            <el-form-item label="货主争议" style="margin-top:14px">
              <el-switch v-model="form.disputed" active-text="货主对损耗有争议（报损暂挂，等待裁定）" />
            </el-form-item>
            <el-form-item v-if="form.disputed" label="争议说明">
              <el-input v-model="form.disputeNote" type="textarea" :rows="2"
                        placeholder="货主主张的损耗率/原因" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" />
            </el-form-item>
            <el-button type="primary" :loading="saving" @click="submit">提交分拣结果并生成待上架货位</el-button>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top:12px" v-if="current">
          <div class="card-title">本批次分拣记录与争议裁定</div>
          <el-table :data="records" size="small">
            <el-table-column prop="sortingNo" label="分拣单号" width="170" class="mono" />
            <el-table-column prop="inputWeight" label="投入" width="70" />
            <el-table-column prop="outputWeight" label="产出" width="70" />
            <el-table-column prop="lossWeight" label="损耗" width="70" />
            <el-table-column label="损耗率" width="80">
              <template #default="{ row }">{{ (row.lossRate*100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="laborMinutes" label="工时min" width="80" />
            <el-table-column label="争议状态">
              <template #default="{ row }">
                <el-tag v-if="row.disputed && !row.disputeResolved" type="danger" size="small">待裁定</el-tag>
                <el-tag v-else-if="row.disputed" type="success" size="small">已裁定</el-tag>
                <el-tag v-else type="info" size="small">无争议</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <template v-if="row.disputed && !row.disputeResolved">
                  <el-button link type="primary" @click="goReview(row)">争议复核</el-button>
                  <el-button link type="warning" @click="openResolve(row)">直接裁定</el-button>
                </template>
                <el-tag v-else-if="row.reviewId" size="small" type="success">已复核</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="resolveVisible" title="分拣损耗争议裁定" width="520px">
      <el-form label-width="120px" v-if="resolveTarget">
        <el-form-item label="登记损耗">
          {{ resolveTarget.lossWeight }} kg（{{ (resolveTarget.lossRate*100).toFixed(1) }}%）
        </el-form-item>
        <el-form-item label="货主说明">
          {{ resolveTarget.disputeNote }}
        </el-form-item>
        <el-form-item label="冷库全赔">
          <el-switch v-model="resolveForm.acceptLoss" active-text="认可全部登记损耗（货主承担）"
                     inactive-text="冷库承担差额" />
        </el-form-item>
        <el-form-item v-if="!resolveForm.acceptLoss" label="裁定损耗kg">
          <el-input-number v-model="resolveForm.adjudicatedLoss" :min="0"
                           :max="Number(resolveTarget.lossWeight)" :precision="2" />
        </el-form-item>
        <el-form-item label="裁定说明">
          <el-input v-model="resolveForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResolve">确认裁定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()

const sortingBatches = ref([])
const categories = ref([])
const records = ref([])
const saving = ref(false)
const current = ref(null)

const defaultItems = () => ([
  { grade: 'A', weight: 0, unitPrice: 0, shelfLifeDays: 5, zoneCode: 'CHILLED' },
  { grade: 'B', weight: 0, unitPrice: 0, shelfLifeDays: 3, zoneCode: 'CHILLED' },
  { grade: 'PROCESS', weight: 0, unitPrice: 0, shelfLifeDays: 2, zoneCode: 'REFRIGERATED' }
])
const form = reactive({
  sorter: '', inputWeight: 0, laborMinutes: 0, disputed: false,
  disputeNote: '', remark: '', sortStation: '', sortVideoUrl: '', badFruitPhotos: '',
  items: defaultItems()
})

const outputTotal = computed(() =>
  form.items.reduce((s, i) => s + Number(i.weight || 0), 0))
const lossWeight = computed(() =>
  Math.max(0, Number(form.inputWeight || 0) - outputTotal.value))
const lossRate = computed(() =>
  Number(form.inputWeight) > 0 ? lossWeight.value / Number(form.inputWeight) : 0)

function catName(id) {
  return categories.value.find(c => c.id === id)?.name || id
}
function remaining(row) {
  return Number(row.netWeight) - Number(row.acceptedWeight || 0)
      - Number(row.lossWeight || 0) - Number(row.returnedWeight || 0)
}

async function onSelect(row) {
  if (!row || !row.id) {
    current.value = null
    records.value = []
    return
  }
  current.value = row
  const cat = categories.value.find(c => c.id === row.categoryId)
  form.items = [
    { grade: 'A', weight: 0, unitPrice: Number(cat?.defaultPriceA || 0), shelfLifeDays: 5,
      zoneCode: cat?.recommendedZone || 'CHILLED' },
    { grade: 'B', weight: 0, unitPrice: Number(cat?.defaultPriceB || 0), shelfLifeDays: 3,
      zoneCode: cat?.recommendedZone || 'CHILLED' },
    { grade: 'PROCESS', weight: 0, unitPrice: Number(cat?.defaultPriceProcess || 0), shelfLifeDays: 2,
      zoneCode: 'REFRIGERATED' }
  ]
  form.inputWeight = Math.max(0, remaining(row))
  records.value = await api.get('/api/sortings/batch/' + row.id)
}

async function submit() {
  if (!current.value) {
    ElMessage.warning('请先选择批次')
    return
  }
  const items = form.items.filter(i => Number(i.weight) > 0)
  if (!items.length) {
    ElMessage.warning('至少填写一个等级的重量')
    return
  }
  saving.value = true
  try {
    await api.post('/api/sortings', {
      batchId: current.value.id,
      sorter: form.sorter || '分拣员',
      inputWeight: form.inputWeight,
      laborMinutes: form.laborMinutes,
      disputed: form.disputed,
      disputeNote: form.disputeNote,
      remark: form.remark,
      sortStation: form.sortStation,
      sortVideoUrl: form.sortVideoUrl,
      badFruitPhotos: form.badFruitPhotos,
      items
    })
    ElMessage.success('分拣完成，已生成待上架货位批次')
    await load()
    const next = sortingBatches.value.find(b => b.id === current.value.id)
    if (next) await onSelect(next)
  } finally {
    saving.value = false
  }
}

const resolveVisible = ref(false)
const resolveTarget = ref(null)
const resolveForm = reactive({ acceptLoss: true, adjudicatedLoss: 0, note: '' })

function openResolve(row) {
  resolveTarget.value = row
  resolveForm.acceptLoss = true
  resolveForm.adjudicatedLoss = Number(row.lossWeight)
  resolveForm.note = ''
  resolveVisible.value = true
}

/** 跳转争议复核工作台（货主就该争议分拣单发起正式复核） */
function goReview(row) {
  router.push({ path: '/loss-reviews', query: { sortingId: row.id } })
}

async function submitResolve() {
  await api.post(`/api/sortings/${resolveTarget.value.id}/resolve-dispute`, {
    acceptLoss: resolveForm.acceptLoss,
    adjudicatedLoss: resolveForm.acceptLoss ? resolveTarget.value.lossWeight : resolveForm.adjudicatedLoss,
    note: resolveForm.note
  })
  ElMessage.success('争议已裁定，账单口径已更新')
  resolveVisible.value = false
  if (current.value) records.value = await api.get('/api/sortings/batch/' + current.value.id)
}

async function load() {
  const [s, i] = await Promise.all([
    api.get('/api/batches?status=SORTING,INSPECTED'),
    api.get('/api/masters/categories')
  ])
  sortingBatches.value = s
  categories.value = i
  if (!current.value && s.length) await onSelect(s[0])
}

onMounted(load)
</script>

<style scoped>
.sum-row {
  display: flex; gap: 26px; margin-top: 12px;
  background: #f7f9fa; border-radius: 8px; padding: 10px 14px; font-size: 14px;
}
</style>
