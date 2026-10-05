#!/usr/bin/env python3
"""端到端业务链验证：到货→质检(签名)→分拣(视频/照片/工位)→损耗争议复核(抽样/周转筐证据链)
→上架→预付款→出库→投诉→温区故障转仓→报损→账单(复核口径)→追溯→看板。"""
import json
import urllib.request
import urllib.error

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

# ---------- 5. 批次A 质检转分拣（含质检员电子签名，作为复核平台证据） ----------
call("POST", "/api/inspections", {
    "batchId": B1, "inspector": "李检",
    "appearanceScore": 82, "maturityScore": 85, "rotRate": 0.02, "moistureRate": 0.88,
    "measuredTemp": 9, "certificateOk": True, "mixedConfirmed": True,
    "decision": "SORT", "signature": "李检（电子签名）",
    "remark": "温度偏高，需分拣分级后速入冰温库"
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
# 同时上传分拣视频、坏果照片、分拣工位——争议复核时平台向货主公开展示
sort1 = call("POST", "/api/sortings", {
    "batchId": B1, "sorter": "孙姐", "inputWeight": 1000, "laborMinutes": 90,
    "sortStation": "1号分拣台",
    "sortVideoUrl": "VIDEO-SORT-20261004-01",
    "badFruitPhotos": "PHOTO-BAD-001,PHOTO-BAD-002",
    "disputed": True, "disputeNote": "货主主张运输途中掉粒属正常，损耗应≤3%",
    "items": [
        {"grade": "A", "weight": 600, "unitPrice": 12.0, "shelfLifeDays": 6},
        {"grade": "B", "weight": 330, "unitPrice": 8.0, "shelfLifeDays": 4},
        {"grade": "PROCESS", "weight": 20, "unitPrice": 3.0, "shelfLifeDays": 2}
    ]
})
SID = sort1["id"]

# ---------- 8b. 货主发起损耗争议复核（平台展示原入库重量/视频/坏果照片/等级/质检员签名） ----------
import datetime as _dt
def _t(minutes_ago):
    return (_dt.datetime.now() - _dt.timedelta(minutes=minutes_ago)).strftime("%Y-%m-%dT%H:%M:%S")

# 录入四个周转筐的装卸/搬运时间线（筐号↔工位↔搬运时间）
crates = [
    # K-07：底层筐、码放7层高，挤压风险高，到台10分钟即开拣
    {"crateNo": "K-07", "stackLayer": 1, "stackHeight": 7, "sortStation": "1号分拣台",
     "arrivedAt": _t(100), "unloadedAt": _t(80), "reachedStationAt": _t(70), "sortingStartedAt": _t(60),
     "handler": "装卸班·老吴"},
    # K-12：到台后 50 分钟才开拣，分拣台长时间暴露
    {"crateNo": "K-12", "stackLayer": 4, "stackHeight": 4, "sortStation": "1号分拣台",
     "arrivedAt": _t(115), "unloadedAt": _t(90), "reachedStationAt": _t(75), "sortingStartedAt": _t(25),
     "handler": "装卸班·老吴"},
    {"crateNo": "K-05", "stackLayer": 3, "stackHeight": 4, "sortStation": "1号分拣台",
     "arrivedAt": _t(60), "unloadedAt": _t(55), "reachedStationAt": _t(50), "sortingStartedAt": _t(45),
     "handler": "装卸班·老吴"},
    {"crateNo": "K-18", "stackLayer": 5, "stackHeight": 5, "sortStation": "1号分拣台",
     "arrivedAt": _t(50), "unloadedAt": _t(45), "reachedStationAt": _t(40), "sortingStartedAt": _t(33),
     "handler": "装卸班·老吴"},
]
for c in crates:
    call("POST", "/api/review-support/crate-handling", {"batchId": B1, "sortingId": SID, **c})

rv = call("POST", "/api/loss-reviews", {
    "sortingId": SID, "ownerClaimLossRate": 0.03,
    "ownerClaimNote": "货主主张运输途中掉粒属正常，损耗应≤3%",
    "reviewer": "周复审"
})
RID = rv["id"]
# 重复发起应被拒绝
call("POST", "/api/loss-reviews", {"sortingId": SID, "reviewer": "周复审"}, expect=400)

# ---------- 8c. 复核员重新抽样（两次）：累计坏果率 3% → 复核损耗 30kg、可售 970kg ----------
call("POST", f"/api/loss-reviews/{RID}/samples", {
    "samplePoint": "三号位/K-07", "sampleWeight": 100, "rotWeight": 6,
    "gradeAWeight": 70, "gradeBWeight": 22, "gradeProcessWeight": 2,
    "operator": "周复审", "remark": "底层筐坏果偏多"
})
s2 = call("POST", f"/api/loss-reviews/{RID}/samples", {
    "samplePoint": "一号位/K-05", "sampleWeight": 100, "rotWeight": 0,
    "gradeAWeight": 75, "gradeBWeight": 25, "operator": "周复审"
})
rd = call("GET", f"/api/loss-reviews/{RID}")
assert float(rd["review"]["sampleRotRate"]) == 0.03, "累计抽样坏果率应为 3%"
assert float(rd["review"]["reviewedLossWeight"]) == 30.0, "推算复核损耗应为 30kg"
assert float(rd["review"]["reviewedSaleableWeight"]) == 970.0, "调整后可售应为 970kg"
assert rd["sorting"]["sortVideoUrl"] == "VIDEO-SORT-20261004-01"
assert "PHOTO-BAD-001" in rd["sorting"]["badFruitPhotos"]
assert rd["inspection"]["signature"] == "李检（电子签名）"
assert len(rd["gradeItems"]) == 3 and len(rd["crateHandling"]) == 4

# ---------- 8d. 复核结案：坏果集中 K-07(装卸挤压)/K-12(分拣台暴露)，多因混合，冷库承担20kg ----------
conc = call("POST", f"/api/loss-reviews/{RID}/conclude", {
    "lossCause": "MIXED", "responsibility": "WAREHOUSE",
    "reviewedSaleableWeight": 970, "crateConcentrated": True,
    "sortTeam": "晚班分拣一组",
    "crates": [
        {"crateNo": "K-07", "badFruitWeight": 12, "damagePattern": "底层挤压破皮压伤、掉粒"},
        {"crateNo": "K-12", "badFruitWeight": 6, "damagePattern": "萎蔫掉粒、果梗发干"}
    ],
    "conclusion": "抽样坏果率3%：K-07 底层码7层装卸挤压、K-12 到台暴露50分钟，合计20kg为冷库操作不当，其余30kg为到货高温质量损耗。",
    "reviewerSignature": "周复审"
})
assert float(conc["warehouseBorneWeight"]) == 20.0, "冷库承担重量应为 20kg"
assert float(conc["compensateAmount"]) == 208.40, "冷库赔付应=20kg×分拣均价10.42=208.40元"
assert conc["inspectorDeductedPoints"] == 10, "抽样坏果率3%>质检腐损率2%，质检员漏检应扣10分"
assert conc["inspectorName"] == "李检"
assert conc["customerCompensationId"] is not None
assert "K-07" in conc["crateAnalysis"] and "K-12" in conc["crateAnalysis"]
assert "装卸挤压" in conc["crateAnalysis"] and "分拣台长时间暴露" in conc["crateAnalysis"]

# 结案后不能再抽样
call("POST", f"/api/loss-reviews/{RID}/samples",
     {"sampleWeight": 10, "rotWeight": 1}, expect=400)
# 已结案列表可查到
done_list = call("GET", "/api/loss-reviews?status=CONCLUDED")
assert any(r["id"] == RID for r in done_list)
# 分拣单已回写复核单ID
sortings_b1 = call("GET", f"/api/sortings/batch/{B1}")
assert sortings_b1[0]["reviewId"] == RID
assert float(sortings_b1[0]["adjudicatedLossWeight"]) == 30.0

# 质检员考核汇总：李检 90 分（扣10）
perf = call("GET", "/api/review-support/inspector-performance/summary")
li = [p for p in perf if p["inspector"] == "李检"][0]
assert li["score"] == 90 and li["missJudgeCount"] == 1 and li["deductedPoints"] == 10
comps = call("GET", "/api/review-support/compensations")
mycomp = [c for c in comps if c["reviewId"] == RID][0]
assert float(mycomp["amount"]) == 208.40 and mycomp["sortTeam"] == "晚班分拣一组"
assert mycomp["billWritten"] is False, "账单生成前赔付记录为待入账"
# 分拣班组考核汇总
teams = call("GET", "/api/review-support/team-performance")
team = [t for t in teams if t["sortTeam"] == "晚班分拣一组"][0]
assert team["caseCount"] == 1 and abs(float(team["borneWeight"]) - 20.0) < 0.01 \
    and abs(float(team["compensateAmount"]) - 208.4) < 0.01, team

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
    print(f"    {it['type']:18s} {it['summary'][:46]:48s} 金额={it['amount']}")
# 复核结论必须回写账单：冷库赔付 20kg × 10.42 = 208.4；货主承担 30kg 不计价；赔付台账留痕
review_lines = [it for it in items if it["summary"].startswith("争议复核")]
assert any("冷库赔付" in it["summary"] and abs(float(it["amount"]) - 208.40) < 0.01 for it in review_lines), \
    "账单缺少争议复核冷库赔付 208.40 元"
assert any("货主承担损耗" in it["summary"] and abs(float(it["weight"]) - 30.0) < 0.01 for it in review_lines), \
    "账单缺少争议复核货主承担 30kg"
assert any(it["summary"].startswith("客户赔付台账") for it in items), "账单缺少客户赔付台账留痕"
call("POST", f"/api/bills/{FID}/confirm", None)
settled = call("POST", f"/api/bills/{FID}/settle", {})
print(f"  应付货主净额={settled['netPayable']} 预付款抵扣={settled['advanceDeducted']} 实付={settled['paidAmount']}")
# 8150 销售 + 268.4 冷库补偿（复核208.4 + 加工级报损60）- 332.5 仓储 - 570 分拣 - 54 工时 - 300 投诉 - 2000 预付
assert abs(float(settled['netPayable']) - 5161.90) < 0.05, f"应付货主净额应为 5161.90，实际 {settled['netPayable']}"
# 账单生成后赔付记录回写为已入账
comps2 = call("GET", "/api/review-support/compensations")
assert [c for c in comps2 if c["reviewId"] == RID][0]["billWritten"] is True

# ---------- 17. 退货批次不允许账单 ----------
call("POST", "/api/bills/generate", {"batchId": B3}, expect=400)

# ---------- 18. 追溯链 ----------
chain = call("GET", f"/api/trace/batch/{B1}")
print(f"  追溯时间线节点数={len(chain['timeline'])}，出库流向={[(f['customer'], f['weight']) for f in chain['finalFlow']]}")
stages = [e["stage"] for e in chain["timeline"]]
for s in ["ARRIVAL", "INSPECTION", "SORTING", "LOSS_REVIEW", "PUT_IN", "OUTBOUND",
          "COMPLAINT", "SETTLEMENT"]:
    assert s in stages, f"追溯链缺少 {s}"
assert len(chain["lossReviews"]) == 1 and chain["lossReviews"][0]["id"] == RID
assert len(chain["compensations"]) == 1 and abs(float(chain["compensations"][0]["amount"]) - 208.4) < 0.01
ce = chain["crateEvidences"]
assert {c["crateNo"] for c in ce} == {"K-07", "K-12"}
k07 = [c for c in ce if c["crateNo"] == "K-07"][0]
k12 = [c for c in ce if c["crateNo"] == "K-12"][0]
assert k07["indicatedCause"] == "LOADING_SQUEEZE", f"K-07 应判装卸挤压，实际 {k07['indicatedCause']}"
assert k12["indicatedCause"] == "SORT_EXPOSURE", f"K-12 应判分拣台暴露，实际 {k12['indicatedCause']}"
assert k07["sortStation"] == "1号分拣台" and k12["exposedMinutes"] == 50

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
# 复核调整：分拣950 + 冷库承担回补20 = 可售970；复核货主承担30 + 加工级报损20 = 损耗50
assert float(final_b1['acceptedWeight']) == 970.0
assert float(final_b1['lossWeight']) == 50.0
assert float(final_b1['outWeight']) == 800.0

# ---------- 21. 未抽样不能结案、无争议不能发起复核（负向校验） ----------
b4 = call("POST", "/api/batches/arrival", {
    "ownerId": 2, "categoryId": 2, "origin": "青山乡莓香村",
    "productionDate": "2026-10-05", "grossWeight": 100, "arrivalTemp": 3, "packaging": "纸盒",
    "certificateNo": "QY-CM-20261005-09", "issuingOrg": "县农检中心",
    "certIssueDate": "2026-10-05", "certificateVerified": True,
    "vehicleNo": "鲁Q·K2231", "mixedLoad": False,
    "plannedCustomerId": 2, "settleMethod": "BUYOUT", "operator": "赵调度"
})
B4 = b4["id"]
call("POST", "/api/inspections", {
    "batchId": B4, "inspector": "李检", "appearanceScore": 90, "maturityScore": 90,
    "rotRate": 0.02, "moistureRate": 0.9, "measuredTemp": 3, "certificateOk": True,
    "decision": "SORT", "signature": "李检（电子签名）"
})
s4 = call("POST", "/api/sortings", {
    "batchId": B4, "sorter": "孙姐", "inputWeight": 100, "laborMinutes": 20,
    "sortStation": "2号分拣台", "disputed": False,
    "items": [{"grade": "A", "weight": 95, "unitPrice": 25.0, "shelfLifeDays": 5}]
})
# 无争议分拣单不能发起复核
call("POST", "/api/loss-reviews", {"sortingId": s4["id"], "reviewer": "周复审"}, expect=400)

print("\n" + ("=" * 60))
if failures:
    print(f"存在 {len(failures)} 个失败：")
    for f in failures:
        print(" -", f)
    raise SystemExit(1)
print("全部业务链验证通过 ✅")
