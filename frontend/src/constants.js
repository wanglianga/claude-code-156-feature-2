// 与后端 DomainEnums 对齐的展示字典
export const ENUMS = {
  BatchStatus: {
    REGISTERED: '待检', INSPECTED: '已质检', SORTING: '分拣中',
    IN_STORAGE: '在库', PARTIAL_OUT: '部分出库', OUT: '全部出库', RETURNED: '已退货'
  },
  Decision: { ACCEPT: '准予入库', SORT: '转分拣', DOWNGRADE: '降级入库', RETURN: '退货' },
  Grade: { A: 'A级精品', B: 'B级通货', PROCESS: '加工级' },
  ZoneType: { CHILLED: '冰温保鲜区', REFRIGERATED: '冷藏区', FROZEN: '冷冻区' },
  SettleMethod: { CONSIGN: '代销结算', BUYOUT: '保底收购', SELF: '货主自销代结' },
  OrderStatus: { PLANNED: '已排单待出库', OUT: '已出库' },
  BillStatus: { DRAFT: '待确认', CONFIRMED: '已确认', PAID: '已结算' },
  Responsibility: {
    OWNER: '货主责任', WAREHOUSE: '冷库责任', CARRIER: '承运责任', CUSTOMER: '客户责任', PENDING: '待裁定'
  },
  ReviewStatus: { OPEN: '待复核', SAMPLING: '重新抽样中', CONCLUDED: '已复核结案' },
  LossCause: {
    ARRIVAL_QUALITY: '到货质量差', LOADING_SQUEEZE: '装卸挤压',
    SORT_EXPOSURE: '分拣台长时间暴露', MIXED: '多因混合', NORMAL: '正常自然损耗'
  },
  CrateStage: { LOADING: '装车/到货筐', SORT_STATION: '分拣工位筐', TRANSFER: '搬运周转筐' },
  ComplaintType: { BAD_FRUIT: '坏果腐损', SHORT_WEIGHT: '短斤少两', TEMP_ABUSE: '温度失控', OTHER: '其他' },
  AlertType: {
    TEMP_EXCEEDED: '到货温度超标', MIXED_LOAD: '混批混装', CUSTOMER_CHANGED: '临时改客户',
    PRIORITY_OUT: '客户要求优先出库', ZONE_FAULT: '冷库温区故障', LOSS_DISPUTE: '分拣损耗争议',
    ORIGINAL_WEIGHT_SETTLE: '按原重结算争议', COMPLAINT: '出库后客户投诉',
    HIGH_STAY_DAYS: '在仓超时高风险', LOCK_PRICE: '锁价客户待履约'
  },
  Stage: {
    ARRIVAL: '到货登记', INSPECTION: '质检验收', SORTING: '分拣处理', PUT_IN: '入库上架',
    TRANSFER: '转仓移库', LOSS: '报损', OUTBOUND: '出库发运', COMPLAINT: '客户投诉',
    LOSS_REVIEW: '损耗复核', SETTLEMENT: '财务结算'
  },
  BillItemType: {
    SALE: '销售收入', STORAGE_FEE: '仓储费', SORT_FEE: '分拣费', LABOR_FEE: '分拣工时费',
    LOSS_DEDUCT: '损耗补偿/扣款', COMPLAINT_DEDUCT: '投诉赔付扣款', ADVANCE_DEDUCT: '预付款抵扣',
    RETURN_DEDUCT: '退货扣款', REMAIN_VALUE: '期末库存货值'
  }
}

export const STATUS_TAG = {
  REGISTERED: 'info', INSPECTED: 'primary', SORTING: 'warning', IN_STORAGE: 'success',
  PARTIAL_OUT: 'warning', OUT: 'success', RETURNED: 'danger',
  PLANNED: 'warning', OUT_ORDER: 'success',
  DRAFT: 'info', CONFIRMED: 'warning', PAID: 'success'
}

export function desc(group, key) {
  return ENUMS[group]?.[key] || key || '-'
}
