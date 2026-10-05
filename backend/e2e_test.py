#!/usr/bin/env python3
"""端到端业务链验证：到货→质检→分拣→上架→预付款→出库→投诉→温区故障转仓→报损→账单→追溯→看板。"""
import json
import urllib.request
import urllib.error
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080"
token = None
failures = []


def call(method, path, body=None, expect=200):
    import urllib.parse
    url = BASE + urllib.parse.quote(path, safe="/?=&%:")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            txt = r.read().decode("utf-8")
            status = r.status
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8")
        status = e.code
    ok = status == expect
    if not ok:
        failures.append(f"{method} {path} -> {status} (expect {expect}): {txt[:300]}")
    try:
        parsed = json.loads(txt)
    except Exception:
        parsed = txt
    print(f"[{'OK' if ok else 'FAIL'}] {method} {path} -> {status}")
    return parsed


# ---------- 1. 登录 ----------
r = call("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
token = r["token"]

# ---------- 2. 批次A：葡萄，温度超标(12℃>8℃)+混批+锁价客户+正常分拣 ----------
b1 = call("POST", "/api/batches/arrival", {
    "ownerId": 1, "categoryId": 1, "origin": "卧龙镇沙沟村三组",
    "productionDate": "2026-10-03", "grossWeight": 1020, "tareWeight": 20,
    "arrivalTemp": 12, "packaging": "泡沫箱加冰",
    "certificateNo": "QY-PT-20261004-01", "issuingOrg": "县农检中心",
    "certIssueDate": "2026-10-04", "certificateVerified": True,
    "vehicleNo": "鲁Q·G8816", "mixedLoad": True,
    "plannedCustomerId": 1, "settleMethod": "CONSIGN", "lockedPrice": 11.5,
    "operator": "赵调度"
})
B1 = b1["id"]
assert b1["netWeight"] == 1000.0

# ---------- 3. 批次B：草莓 300kg，正常到货，质检直接入库 ----------
b2 = call("POST", "/api/batches/arrival", {
    "ownerId": 2, "categoryId": 2, "origin": "青山乡莓香村",
    "productionDate": "2026-10-04", "grossWeight": 300,
    "arrivalTemp": 3, "packaging": "纸盒",
    "certificateNo": "QY-CM-20261004-02", "issuingOrg": "县农检中心",
    "certIssueDate": "2026-10-04", "certificateVerified": True,
    "vehicleNo": "鲁Q·G8816", "mixedLoad": True,
    "plannedCustomerId": 2, "settleMethod": "BUYOUT",
    "operator": "赵调度"
})
B2 = b2["id"]

# ---------- 4. 批次C：叶菜 200kg，温度不高但质检查退 ----------
b3 = call("POST", "/api/batches/arrival", {
    "ownerId": 3, "categoryId": 3, "origin": "城北菜园",
    "productionDate": "2026-10-04", "grossWeight": 200,
    "arrivalTemp": 5, "packaging": "筐装",
    "certificateNo": "QY-YC-20261004-03", "issuingOrg": "县农检中心",
    "certIssueDate": "2026-10-04", "certificateVerified": True,
    "vehicleNo": "鲁Q·K2230", "mixedLoad": False,
    "plannedCustomerId": 4, "settleMethod": "CONSIGN",
    "operator": "赵调度"
})
B3 = b3["id"]

# ---------- 5. 批次A 质检转分拣 ----------
call("POST", "/api/inspections", {
    "batchId": B1, "inspector": "李检",
    "appearanceScore": 82, "maturityScore": 85, "rotRate": 0.02, "moistureRate": 0.88,
    "measuredTemp": 9, "certificateOk": True, "mixedConfirmed": True,
    "decision": "SORT", "remark": "温度偏高，需分拣分级后速入冰温库"
})

# ---------- 6. 批次B 质检直接入库 A级，核定损耗2% ----------
in2 = call("POST", "/api/inspections", {
    "batchId": B2, "inspector": "李检",
    "appearanceScore": 95, "maturityScore": 90, "rotRate": 0.01, "moistanceRate": 0.9,
    "moistureRate": 0.9, "measuredTemp": 3, "certificateOk": True,
    "decision": "ACCEPT", "suggestedGrade": "A", "estimatedLossRate": 0.02
})

# ---------- 7. 批次C 退货 ----------
call("POST", "/api/inspections", {
    "batchId": B3, "inspector": "李检",
    "appearanceScore": 40, "maturityScore": 70, "rotRate": 0.18, "moistureRate": 0.95,
    "measuredTemp": 5, "certificateOk": True,
    "decision": "RETURN", "returnWeight": 200,
    "remark": "烂叶超过15%，整批退回"
})

# ---------- 8. 批次A 分拣：A 600@12, B 330@8, 加工 20@3, 合计950，损耗50(5%)，工时90分钟，货主争议 ----------
sort1 = call("POST", "/api/sortings", {
    "batchId": B1, "sorter": "孙姐", "inputWeight": 1000, "laborMinutes": 90,
    "disputed": True, "disputeNote": "货主主张运输途中掉粒属正常，损耗应≤3%",
    "items": [
        {"grade": "A", "weight": 600, "unitPrice": 12.0, "shelfLifeDays": 6},
        {"grade": "B", "weight": 330, "unitPrice": 8.0, "shelfLifeDays": 4},
        {"grade": "PROCESS", "weight": 20, "unitPrice": 3.0, "shelfLifeDays": 2}
    ]
})
SID = sort1["id"]

# ---------- 8.1 货主发起分拣损耗争议复核（平台固定证据快照） ----------
rv = call("POST", "/api/loss-reviews/apply", {
    "sortingId": SID, "applicant": "货主张守田",
    "claimedLossRate": 0.03,
    "claimReason": "货主认为分拣后损耗比例过高，主张坏果多为分拣/搬运环节造成，损耗应≤3%"
})
RID = rv["id"]
assert rv["status"] == "OPEN"
assert rv["originalNetWeight"] == 1000.0, "证据快照应含原始入库净重"
assert rv["sortingInputWeight"] == 1000.0 and rv["sortingOutputWeight"] == 950.0
assert rv["sortingLossWeight"] == 50.0 and float(rv["sortingLossRate"]) == 0.05
assert "VIDEO" in rv["sortingVideo"], "应自动生成分拣视频调阅编号"
assert "PHOTO" in rv["badFruitPhotos"], "应自动生成坏果照片编号"
assert rv["qcSignature"] == "李检", "应快照质检员签名"
assert "A级精品" in rv["gradeBreakdown"] and "B级通货" in rv["gradeBreakdown"]
print(f"  复核单 {rv['reviewNo']} 证据已固定：原重 {rv['originalNetWeight']}kg / 视频 / 坏果照片 / 等级划分 / 质检员 {rv['qcSignature']} 签名")

# 同一分拣单重复发起 → 400
call("POST", "/api/loss-reviews/apply", {"sortingId": SID, "claimedLossRate": 0.03}, expect=400)

# ---------- 8.2 登记坏果周转筐线索：筐号 + 分拣工位 + 搬运时间串联 ----------
t0 = datetime(2026, 10, 5, 8, 0, 0)
def dt(d):
    return d.strftime("%Y-%m-%d %H:%M:%S")

# 筐1：台面暴露 45 分钟 → 分拣台长时间暴露
bk1 = call("POST", f"/api/loss-reviews/{RID}/baskets", {
    "basketNo": "K-0231", "workstation": "A区3号台",
    "sortStationAt": dt(t0), "movedAwayAt": dt(t0 + timedelta(minutes=45)),
    "mover": "分拣一班", "moveStage": "上台分拣", "badRate": 0.18,
    "causeBasis": "葡萄粒萎蔫掉粒，台面停留过久"
})
assert bk1["cause"] == "SORT_EXPOSURE", f"暴露45分钟应判长时间暴露，实际 {bk1['cause']}"
# 筐2：暴露仅 10 分钟，入库搬运环节挤压痕迹 → 装卸挤压
bk2 = call("POST", f"/api/loss-reviews/{RID}/baskets", {
    "basketNo": "K-0240", "workstation": "B区1号台",
    "sortStationAt": dt(t0 + timedelta(hours=1)),
    "movedAwayAt": dt(t0 + timedelta(hours=1, minutes=10)),
    "mover": "装卸二班", "moveStage": "入库搬运", "badRate": 0.22,
    "causeBasis": "底层筐果粒破裂流汁，堆码挤压痕迹"
})
assert bk2["cause"] == "LOADING_SQUEEZE"
# 筐3：上台 8 分钟即发现坏果，无挤压 → 来货质量差
bk3 = call("POST", f"/api/loss-reviews/{RID}/baskets", {
    "basketNo": "K-0255", "workstation": "A区1号台",
    "sortStationAt": dt(t0 + timedelta(hours=2)),
    "movedAwayAt": dt(t0 + timedelta(hours=2, minutes=8)),
    "mover": "分拣一班", "moveStage": "上台分拣", "badRate": 0.15,
    "causeBasis": "上台即见软烂霉变果，与到货高温一致"
})
assert bk3["cause"] == "ARRIVAL_QUALITY"

# ---------- 8.3 复核员重新抽样 ----------
smp = call("POST", f"/api/loss-reviews/{RID}/sample", {
    "sampleWeight": 50, "sampleBadWeight": 7, "sampler": "复核员·周仲",
    "sampleNote": "抽样坏果率14%，高于登记损耗率；坏果分三类：霉变/挤压破裂/失水萎蔫"
})
assert smp["status"] == "SAMPLING" and float(smp["sampleBadRate"]) == 0.14
# 出具结论后禁止再抽样（稍后复验）

# ---------- 8.4 出具复核结论：多方因素分摊（来货20kg货主 / 操作15kg冷库） ----------
concl = call("POST", f"/api/loss-reviews/{RID}/conclude", {
    "conclusion": "MIXED", "confirmedLossWeight": 35,
    "ownerBorneWeight": 20, "warehouseBorneWeight": 15,
    "compensatePrice": 10.42,
    "customerCompNeeded": True, "customerId": 1, "customerCompAmount": 100,
    "customerCompNote": "锁价客户已提 A 级货中检出坏果，冷库主动赔付 100 元",
    "reviewer": "复核员·周仲",
    "conclusionNote": "K-0255 上台即坏属来货质量（货主担20kg）；K-0231 超时暴露、K-0240 搬运挤压属冷库操作（担15kg），按10.42元/kg赔付"
})
assert concl["status"] == "CONFIRMED"
assert float(concl["ownerBorneWeight"]) == 20
assert float(concl["warehouseBorneWeight"]) == 15
assert float(concl["salableAdjustWeight"]) == 15
assert float(concl["compensateAmount"]) == 156.30, f"冷库赔付应为 15*10.42=156.3，实际 {concl['compensateAmount']}"
assert float(concl["customerCompAmount"]) == 100

rd = call("GET", f"/api/loss-reviews/{RID}")
assert len(rd["baskets"]) == 3
targets = sorted(a["target"] for a in rd["assessments"])
assert targets == ["LOADING_TEAM", "QC_INSPECTOR", "SORT_TEAM"], f"考核应覆盖三类对象：{targets}"
deducts = {a["target"]: float(a["scoreChange"]) for a in rd["assessments"]}
assert deducts["SORT_TEAM"] == 5 and deducts["LOADING_TEAM"] == 5, "分拣/装卸班组各扣5分"
comp0 = rd["compensations"][0]
assert comp0["status"] == "待赔付" and float(comp0["amount"]) == 100
CID_COMP = comp0["id"]
print(f"  复核结论：货主担20kg，冷库担15kg赔付156.3元，可售回补15kg；考核 {targets}；客户赔付待付")

# 已出具结论后不能再抽样/再结论
call("POST", f"/api/loss-reviews/{RID}/sample",
     {"sampleWeight": 10, "sampleBadWeight": 1, "sampler": "x"}, expect=400)

# 客户赔付到账
paid = call("POST", f"/api/loss-reviews/compensations/{CID_COMP}/pay?operator=钱会计")
assert paid["status"] == "已赔付"

# 台账可查
assess_all = call("GET", "/api/loss-reviews/assessments")
comp_all = call("GET", "/api/loss-reviews/compensations")
assert any(a["reviewId"] == RID for a in assess_all)
assert any(c["reviewId"] == RID and c["status"] == "已赔付" for c in comp_all)

# ---------- 9. 上架：批次A三个货位 → CW-01 三个库位；批次B待上架 lot ----------
pending = call("GET", "/api/stock/pending")
lots_b1 = sorted([p for p in pending if p["batchId"] == B1], key=lambda x: x["grade"])
# A -> loc1, B -> loc2, PROCESS -> loc3
for lot, loc_id in zip(lots_b1, [1, 2, 3]):
    call("POST", "/api/stock/put-away", {"lotId": lot["id"], "locationId": loc_id, "operator": "赵调度"})

lots_b2 = [p for p in call("GET", "/api/stock/pending") if p["batchId"] == B2]
call("POST", "/api/stock/put-away", {"lotId": lots_b2[0]["id"], "locationId": 1, "operator": "赵调度"})

# ---------- 10. 货主预付款放款 2000 ----------
call("POST", "/api/owners/1/advance", {"batchId": B1, "amount": 2000, "remark": "葡萄采收季预付款"})

# ---------- 11. 出库：锁价客户1 提 A级 500kg（优先） ----------
lot_a = [l for l in call("GET", "/api/stock/in-storage") if l["batchId"] == B1 and l["grade"] == "A"][0]
o1 = call("POST", "/api/outbounds/plan", {
    "customerId": 1, "batchId": B1, "priority": True,
    "operator": "赵调度", "remark": "锁价客户催货",
    "items": [{"lotId": lot_a["id"], "weight": 500}]
})
O1 = o1["id"]
assert o1["priceLocked"] is True, "应命中锁价协议"
assert o1["totalWeight"] == 500.0
# 锁价单价 11.5
assert float(o1["totalAmount"]) == 500 * 11.5
call("POST", f"/api/outbounds/{O1}/execute?operator=赵调度", None)

# ---------- 12. 出库：临时改客户4，提 B级 300kg ----------
lot_b = [l for l in call("GET", "/api/stock/in-storage") if l["batchId"] == B1 and l["grade"] == "B"][0]
o2 = call("POST", "/api/outbounds/plan", {
    "customerId": 4, "batchId": B1,
    "operator": "赵调度", "remark": "原客户暂缓，临时转给社区团购",
    "items": [{"lotId": lot_b["id"], "weight": 300}]
})
O2 = o2["id"]
assert o2["customerChanged"] is True, "应标记临时改客户"
call("POST", f"/api/outbounds/{O2}/execute?operator=赵调度", None)

# ---------- 13. 出库后投诉坏果（针对第一单），裁定货主责任赔付 300 ----------
c1 = call("POST", "/api/complaints", {
    "outboundId": O1, "type": "BAD_FRUIT", "problemWeight": 30,
    "claimAmount": 400, "description": "客户反馈底部三箱坏果约30kg"
})
CID = c1["id"]
call("POST", f"/api/complaints/{CID}/resolve", {
    "responsibility": "OWNER", "settledAmount": 300,
    "handler": "管理员", "handleResult": "查批次链为到货高温导致，货主承担300元"
})

# ---------- 14. 温区故障：冷冻库先报故障（无在库货，验证告警机制）；再让冰温库故障 → B级余货必须转仓 ----------
call("POST", "/api/stock/zones/3/status", {"fault": True, "faultNote": "压缩机告警停机"})
instore = call("GET", "/api/stock/in-storage")
lot_b_left = [l for l in instore if l["batchId"] == B1 and l["grade"] == "B"][0]
# 冰温库故障
call("POST", "/api/stock/zones/1/status", {"fault": True, "faultNote": "蒸发器结霜异常"})
# 转仓到冷藏库 loc4
call("POST", "/api/stock/transfer", {
    "lotId": lot_b_left["id"], "toLocationId": 4, "operator": "赵调度",
    "remark": "冰温库故障，B级葡萄暂转冷藏库"
})
# 故障温区禁止上架校验
call("POST", "/api/stock/put-away", {"lotId": lots_b2[0]["id"], "locationId": 2}, expect=400)
# 修复
call("POST", "/api/stock/zones/1/status", {"fault": False})
call("POST", "/api/stock/zones/3/status", {"fault": False})

# ---------- 15. 库内报损：加工级20kg 冷库责任 ----------
lot_p = [l for l in call("GET", "/api/stock/in-storage") if l["batchId"] == B1 and l["grade"] == "PROCESS"][0]
call("POST", "/api/stock/loss", {
    "lotId": lot_p["id"], "weight": 20, "responsibility": "WAREHOUSE",
    "operator": "赵调度", "remark": "搬运破损"
})

# ---------- 16. 账单：批次A生成→确认→结算 ----------
bill = call("POST", "/api/bills/generate", {"batchId": B1, "remark": "月末账单"})
FID = bill["id"]
items = call("GET", f"/api/bills/{FID}/items")
print("  账单明细：")
for it in items:
    print(f"    {it['type']:22s} {it['summary'][:46]:48s} 金额={it['amount']}")
review_items = [i for i in items if i["type"] in ("REVIEW_COMPENSATION", "REVIEW_OWNER_LOSS",
                                                  "CUSTOMER_COMPENSATION")]
assert any(i["type"] == "REVIEW_COMPENSATION" and float(i["amount"]) == 156.30 for i in items), \
    "账单应含复核冷库赔付 15kg×10.42=156.30"
assert any(i["type"] == "REVIEW_OWNER_LOSS" for i in items), "账单应列示货主自担损耗20kg"
assert any(i["type"] == "CUSTOMER_COMPENSATION" and "P" in i["summary"] for i in items), \
    "账单应备注复核客户赔付记录（不扣货主）"
assert not any("分拣争议·冷库承担损耗补偿" in (i["summary"] or "") for i in items), \
    "已走复核单的分拣单不得再按旧争议口径重复赔付"
# 账单号回写复核单
rd2 = call("GET", f"/api/loss-reviews/{RID}")
assert rd2["review"]["billId"] == FID, "复核单应回写关联账单号"
call("POST", f"/api/bills/{FID}/confirm", None)
settled = call("POST", f"/api/bills/{FID}/settle", {})
# 销售收入8150 + 复核冷库赔付156.30 + 加工级冷库报损60 − 仓储332.5 − 分拣570 − 工时54
# − 投诉赔付300 − 预付款2000 = 5109.80
assert float(settled["netPayable"]) == 5109.80, f"应付货主应为5109.80，实际 {settled['netPayable']}"
print(f"  应付货主净额={settled['netPayable']} 预付款抵扣={settled['advanceDeducted']} 实付={settled['paidAmount']}")

# ---------- 17. 退货批次不允许账单 ----------
call("POST", "/api/bills/generate", {"batchId": B3}, expect=400)

# ---------- 18. 追溯链 ----------
chain = call("GET", f"/api/trace/batch/{B1}")
print(f"  追溯时间线节点数={len(chain['timeline'])}，出库流向={[(f['customer'], f['weight']) for f in chain['finalFlow']]}")
stages = [e["stage"] for e in chain["timeline"]]
for s in ["ARRIVAL", "INSPECTION", "SORTING", "PUT_IN", "OUTBOUND", "COMPLAINT", "SETTLEMENT"]:
    assert s in stages, f"追溯链缺少 {s}"
# 复核数据随批次链可查：3 个筐号线索、3 类考核、1 条客户赔付
assert len(chain["lossReviews"]) == 1
g = chain["lossReviews"][0]
assert len(g["baskets"]) == 3 and len(g["assessments"]) == 3 and len(g["compensations"]) == 1
assert {b["cause"] for b in g["baskets"]} == {"SORT_EXPOSURE", "LOADING_SQUEEZE", "ARRIVAL_QUALITY"}
review_titles = [e["title"] for e in chain["timeline"] if e["stage"] == "SORTING"]
assert any("损耗争议复核" in t for t in review_titles), "追溯链应含复核发起/抽样/结论节点"

# ---------- 19. 看板/调度 ----------
ov = call("GET", "/api/dashboard/overview")
print("  总览：", json.dumps(ov, ensure_ascii=False, default=str)[:300])
mon = call("GET", "/api/dashboard/storage-monitor")
print(f"  在仓监控批次行={len(mon)}")
sug = call("GET", "/api/dashboard/dispatch-suggestions")
print(f"  调度建议条数={len(sug)}：{[s['action'] for s in sug]}")
zones = call("GET", "/api/dashboard/zones")
print("  温区：", [(z['code'], z['occupiedKg'], z['fault']) for z in zones])
alerts = call("GET", "/api/alerts/open")
print(f"  未处理告警={len(alerts)}：{sorted(set(a['type'] for a in alerts))}")

# ---------- 20. 批次最终状态 ----------
final_b1 = call("GET", f"/api/batches/{B1}")
print(f"  批次A状态={final_b1['status']} 原净重={final_b1['netWeight']} 可售={final_b1['acceptedWeight']} "
      f"损耗={final_b1['lossWeight']} 已出={final_b1['outWeight']} 退货={final_b1['returnedWeight']}")

print("\n" + ("=" * 60))
if failures:
    print(f"存在 {len(failures)} 个失败：")
    for f in failures:
        print(" -", f)
    raise SystemExit(1)
print("全部业务链验证通过 ✅")
