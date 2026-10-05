package com.county.coldchain.service;

import com.county.coldchain.domain.DomainEnums.AlertLevel;
import com.county.coldchain.domain.DomainEnums.AlertType;
import com.county.coldchain.domain.DomainEnums.BatchStatus;
import com.county.coldchain.domain.DomainEnums.Decision;
import com.county.coldchain.domain.DomainEnums.Grade;
import com.county.coldchain.domain.DomainEnums.MoveReason;
import com.county.coldchain.domain.DomainEnums.Stage;
import com.county.coldchain.domain.entity.Batch;
import com.county.coldchain.domain.entity.Category;
import com.county.coldchain.domain.entity.Inspection;
import com.county.coldchain.domain.entity.StockLot;
import com.county.coldchain.domain.entity.StockMove;
import com.county.coldchain.repo.BatchRepository;
import com.county.coldchain.repo.CategoryRepository;
import com.county.coldchain.repo.InspectionRepository;
import com.county.coldchain.repo.StockLotRepository;
import com.county.coldchain.repo.StockMoveRepository;
import com.county.coldchain.support.BizException;
import com.county.coldchain.web.dto.InspectionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 质检验收：外观/成熟度/腐损/含水/温度/票证 → 入库 / 降级 / 退货 / 分拣。
 * 直接入库与降级会生成"待上架"货位批次；分拣交由分拣台处理。
 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final BatchRepository batchRepository;
    private final CategoryRepository categoryRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMoveRepository stockMoveRepository;
    private final AlertService alertService;
    private final TraceService traceService;

    @Transactional
    public Inspection inspect(InspectionRequest req) {
        Batch batch = batchRepository.findById(req.getBatchId())
                .orElseThrow(() -> new BizException("批次不存在"));
        if (batch.getStatus() != BatchStatus.REGISTERED) {
            throw new BizException("批次已质检，当前状态：" + batch.getStatus().desc);
        }
        Category cat = categoryRepository.findById(batch.getCategoryId()).orElseThrow();
        Decision decision = Decision.valueOf(req.getDecision());

        Inspection in = new Inspection();
        in.setBatchId(batch.getId());
        in.setInspector(req.getInspector() == null ? "质检员" : req.getInspector());
        in.setInspectedAt(LocalDateTime.now());
        in.setAppearanceScore(req.getAppearanceScore());
        in.setMaturityScore(req.getMaturityScore());
        in.setRotRate(nz(req.getRotRate()));
        in.setMoistureRate(req.getMoistureRate());
        in.setMeasuredTemp(req.getMeasuredTemp());
        in.setCertificateOk(Boolean.TRUE.equals(req.getCertificateOk()));
        in.setMixedConfirmed(Boolean.TRUE.equals(req.getMixedConfirmed()));
        in.setDecision(decision);
        in.setSignature(req.getSignature() == null || req.getSignature().isBlank()
                ? in.getInspector() : req.getSignature());
        in.setRemark(req.getRemark());

        BigDecimal net = batch.getNetWeight();
        String detail;
        switch (decision) {
            case ACCEPT -> {
                Grade grade = req.getSuggestedGrade() == null ? Grade.A : Grade.valueOf(req.getSuggestedGrade());
                BigDecimal lossRate = nz(req.getEstimatedLossRate());
                BigDecimal lossW = net.multiply(lossRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal acceptW = net.subtract(lossW);
                in.setSuggestedGrade(grade);
                in.setEstimatedLossRate(lossRate);
                StockLot lot = createLot(batch, grade, acceptW,
                        grade == Grade.A ? cat.getDefaultPriceA()
                                : grade == Grade.B ? cat.getDefaultPriceB() : cat.getDefaultPriceProcess(),
                        null, "直接入库");
                batch.setAcceptedWeight(acceptW);
                batch.setLossWeight(batch.getLossWeight().add(lossW));
                batch.setStatus(BatchStatus.INSPECTED);
                detail = String.format("准予入库：%s %skg，目测/核定损耗 %skg（%s）",
                        grade.desc, acceptW, lossW, percent(lossRate));
                traceService.log(batch.getId(), lot.getId(), null, Stage.INSPECTION,
                        "质检准予入库", detail + qualitySuffix(in), in.getInspector());
                if (lossRate.compareTo(cat.getAgreedLossRate() == null ? BigDecimal.ONE : cat.getAgreedLossRate()) > 0) {
                    alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.WARN, batch.getId(), null,
                            "核定损耗高于约定 " + batch.getBatchNo(),
                            String.format("质检员核定损耗率 %s 高于约定 %s，货主可能争议。",
                                    percent(lossRate), percent(cat.getAgreedLossRate())), in.getInspector());
                }
            }
            case DOWNGRADE -> {
                Grade grade = req.getSuggestedGrade() == null ? Grade.B : Grade.valueOf(req.getSuggestedGrade());
                if (grade == Grade.A) {
                    throw new BizException("降级结论的建议等级不能为 A 级");
                }
                BigDecimal downW = nz(req.getDowngradeWeight());
                BigDecimal retW = nz(req.getReturnWeight());
                BigDecimal keepW = net.subtract(downW).subtract(retW);
                if (keepW.signum() < 0) {
                    throw new BizException("降级+退货重量超过净重");
                }
                in.setSuggestedGrade(grade);
                in.setDowngradeWeight(downW);
                in.setReturnWeight(retW);
                if (keepW.signum() > 0) {
                    StockLot lot = createLot(batch, grade, keepW, cat.getDefaultPriceB(), null, "降级留存");
                    batch.setAcceptedWeight(keepW);
                }
                if (downW.signum() > 0) {
                    StockLot proc = createLot(batch, Grade.PROCESS, downW, cat.getDefaultPriceProcess(),
                            null, "降级转加工级");
                    batch.setDowngradeWeight(batch.getDowngradeWeight().add(downW));
                    batch.setAcceptedWeight(batch.getAcceptedWeight().add(downW));
                }
                if (retW.signum() > 0) {
                    doReturn(batch, retW, in.getInspector(), "质检不合格部分退货");
                    batch.setReturnedWeight(batch.getReturnedWeight().add(retW));
                }
                batch.setStatus(BatchStatus.INSPECTED);
                detail = String.format("降级入库：%s %skg，加工级 %skg，退货 %skg",
                        grade.desc, keepW, downW, retW);
                traceService.log(batch.getId(), null, null, Stage.INSPECTION,
                        "质检降级处理", detail + qualitySuffix(in), in.getInspector());
            }
            case RETURN -> {
                BigDecimal retW = req.getReturnWeight() == null ? net : req.getReturnWeight();
                in.setReturnWeight(retW);
                doReturn(batch, retW, in.getInspector(), "质检退货：" + nz2(req.getRemark()));
                batch.setReturnedWeight(batch.getReturnedWeight().add(retW));
                batch.setStatus(BatchStatus.RETURNED);
                detail = "退货 " + retW + "kg，批次关闭，不产生仓储/分拣费用";
                traceService.log(batch.getId(), null, null, Stage.INSPECTION,
                        "质检查退退货", detail + qualitySuffix(in), in.getInspector());
            }
            case SORT -> {
                if (!in.getCertificateOk()) {
                    throw new BizException("票证不齐全，不能进入分拣流程");
                }
                batch.setStatus(BatchStatus.SORTING);
                detail = "转分拣：等待分拣台按等级拆分称重定价";
                traceService.log(batch.getId(), null, null, Stage.INSPECTION,
                        "质检转分拣", detail + qualitySuffix(in), in.getInspector());
            }
        }

        // 复检温度仍超标 → 再次告警
        if (in.getMeasuredTemp() != null && (
                (cat.getMaxArrivalTemp() != null && in.getMeasuredTemp().compareTo(cat.getMaxArrivalTemp()) > 0)
                        || (cat.getMinArrivalTemp() != null && in.getMeasuredTemp().compareTo(cat.getMinArrivalTemp()) < 0))) {
            alertService.raise(AlertType.TEMP_EXCEEDED, AlertLevel.CRITICAL, batch.getId(), null,
                    "复检温度仍超标 " + batch.getBatchNo(),
                    String.format("质检员复测 %s℃ 超出 %s 允许区间 [%s,%s]",
                            in.getMeasuredTemp(), cat.getName(), cat.getMinArrivalTemp(), cat.getMaxArrivalTemp()),
                    in.getInspector());
        }
        if (!in.getCertificateOk() && decision != Decision.RETURN) {
            throw new BizException("票证核验不通过：除整批退货外不能入库或分拣");
        }

        inspectionRepository.save(in);
        batchRepository.save(batch);
        return in;
    }

    private StockLot createLot(Batch batch, Grade grade, BigDecimal weight, BigDecimal price,
                               Integer shelfLifeDays, String tag) {
        StockLot lot = new StockLot();
        lot.setLotNo("L" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", stockLotRepository.count() + 1));
        lot.setBatchId(batch.getId());
        lot.setGrade(grade);
        lot.setInitialWeight(weight);
        lot.setCurrentWeight(weight);
        lot.setUnitPrice(price);
        lot.setShelfLifeDays(shelfLifeDays);
        lot.setInAt(LocalDateTime.now());
        lot.setStatus("PENDING");
        return stockLotRepository.save(lot);
    }

    private void doReturn(Batch batch, BigDecimal weight, String operator, String remark) {
        StockMove m = new StockMove();
        m.setBatchId(batch.getId());
        m.setReason(MoveReason.RETURN);
        m.setWeight(weight);
        m.setOperator(operator);
        m.setMovedAt(LocalDateTime.now());
        m.setRemark(remark);
        stockMoveRepository.save(m);
    }

    @Transactional(readOnly = true)
    public java.util.List<Inspection> listByBatch(Long batchId) {
        return inspectionRepository.findByBatchIdOrderByInspectedAtDesc(batchId);
    }

    private String qualitySuffix(Inspection in) {
        return String.format("【外观%s 成熟度%s 腐损率%s 含水率%s 票证%s】",
                in.getAppearanceScore(), in.getMaturityScore(), percent(in.getRotRate()),
                percent(in.getMoistureRate()), Boolean.TRUE.equals(in.getCertificateOk()) ? "齐" : "缺");
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String nz2(String s) { return s == null ? "" : s; }
    private String percent(BigDecimal v) {
        if (v == null) return "-";
        return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
