package com.county.coldchain.service;

import com.county.coldchain.domain.DomainEnums.AlertLevel;
import com.county.coldchain.domain.DomainEnums.AlertType;
import com.county.coldchain.domain.DomainEnums.LossCause;
import com.county.coldchain.domain.DomainEnums.Responsibility;
import com.county.coldchain.domain.DomainEnums.ReviewStatus;
import com.county.coldchain.domain.DomainEnums.Stage;
import com.county.coldchain.domain.entity.Batch;
import com.county.coldchain.domain.entity.CrateEvidence;
import com.county.coldchain.domain.entity.CrateHandlingRecord;
import com.county.coldchain.domain.entity.CustomerCompensation;
import com.county.coldchain.domain.entity.Inspection;
import com.county.coldchain.domain.entity.InspectorPerformance;
import com.county.coldchain.domain.entity.LossReview;
import com.county.coldchain.domain.entity.ReviewSample;
import com.county.coldchain.domain.entity.SortItem;
import com.county.coldchain.domain.entity.SortingRecord;
import com.county.coldchain.repo.AlertRepository;
import com.county.coldchain.repo.BatchRepository;
import com.county.coldchain.repo.CrateEvidenceRepository;
import com.county.coldchain.repo.CrateHandlingRepository;
import com.county.coldchain.repo.CustomerCompensationRepository;
import com.county.coldchain.repo.InspectionRepository;
import com.county.coldchain.repo.InspectorPerformanceRepository;
import com.county.coldchain.repo.LossReviewRepository;
import com.county.coldchain.repo.ReviewSampleRepository;
import com.county.coldchain.repo.SortItemRepository;
import com.county.coldchain.repo.SortingRecordRepository;
import com.county.coldchain.support.BizException;
import com.county.coldchain.web.dto.CrateHandlingRequest;
import com.county.coldchain.web.dto.ReviewConcludeRequest;
import com.county.coldchain.web.dto.ReviewOpenRequest;
import com.county.coldchain.web.dto.ReviewSampleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分拣损耗争议复核：
 * 货主发起 → 平台展示原始入库重量/分拣视频/坏果照片/等级划分/质检员签名 →
 * 复核员重新抽样 → 调整可售重量 → 串联周转筐号、分拣工位、搬运时间判定成因 →
 * 到货质量（货主承担）或分拣操作不当（冷库赔付）→
 * 回写分拣单、货主结算口径、质检员考核、分拣班组与客户赔付记录。
 */
@Service
@RequiredArgsConstructor
public class LossReviewService {

    private final LossReviewRepository reviewRepository;
    private final ReviewSampleRepository sampleRepository;
    private final CrateEvidenceRepository crateEvidenceRepository;
    private final CrateHandlingRepository crateHandlingRepository;
    private final InspectorPerformanceRepository performanceRepository;
    private final CustomerCompensationRepository compensationRepository;
    private final SortingRecordRepository sortingRepository;
    private final SortItemRepository sortItemRepository;
    private final InspectionRepository inspectionRepository;
    private final BatchRepository batchRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final TraceService traceService;

    @Value("${app.review.exposure-warn-minutes:30}")
    private int exposureWarnMinutes;
    @Value("${app.review.squeeze-stack-height:6}")
    private int squeezeStackHeight;
    @Value("${app.review.miss-judge-points:10}")
    private int missJudgePoints;

    // ---------------- 开单 ----------------

    @Transactional
    public LossReview open(ReviewOpenRequest req) {
        SortingRecord sorting;
        if (req.getSortingId() != null) {
            sorting = sortingRepository.findById(req.getSortingId())
                    .orElseThrow(() -> new BizException("分拣单不存在"));
        } else if (req.getBatchId() != null) {
            sorting = sortingRepository.findByBatchIdOrderBySortedAtAsc(req.getBatchId()).stream()
                    .reduce((a, b) -> b)
                    .orElseThrow(() -> new BizException("该批次尚无分拣记录"));
        } else {
            throw new BizException("请指定分拣单或批次");
        }
        if (!Boolean.TRUE.equals(sorting.getDisputed())) {
            throw new BizException("该分拣单无货主争议，不能发起复核");
        }
        if (Boolean.TRUE.equals(sorting.getDisputeResolved())) {
            throw new BizException("该分拣争议已裁定/已复核结案");
        }
        reviewRepository.findBySortingId(sorting.getId()).ifPresent(r -> {
            throw new BizException("该分拣单已存在复核单 " + r.getReviewNo() + "（状态：" + r.getStatus().desc + "）");
        });
        Batch batch = batchRepository.findById(sorting.getBatchId()).orElseThrow();

        LossReview r = new LossReview();
        r.setReviewNo("R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", reviewRepository.count() + 1));
        r.setBatchId(batch.getId());
        r.setSortingId(sorting.getId());
        r.setOwnerId(batch.getOwnerId());
        r.setOwnerClaimLossRate(req.getOwnerClaimLossRate());
        r.setOwnerClaimNote(req.getOwnerClaimNote());
        r.setReviewer(req.getReviewer() == null ? "复核员" : req.getReviewer());
        r.setStartedAt(LocalDateTime.now());
        r.setStatus(ReviewStatus.OPEN);
        r = reviewRepository.save(r);

        alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.CRITICAL, batch.getId(), null,
                "货主申请损耗争议复核 " + batch.getBatchNo(),
                String.format("复核单 %s：货主不认可分拣单 %s 的 %skg（%s）损耗，主张上限 %s。平台已展示原入库净重 %skg、分拣视频、坏果照片、等级划分与质检员签名，等待重新抽样复核。",
                        r.getReviewNo(), sorting.getSortingNo(), sorting.getLossWeight(),
                        percent(sorting.getLossRate()),
                        req.getOwnerClaimLossRate() == null ? "未填" : percent(req.getOwnerClaimLossRate()),
                        batch.getNetWeight()),
                r.getReviewer());
        traceService.log(batch.getId(), null, null, Stage.LOSS_REVIEW,
                "货主申请损耗争议复核 " + r.getReviewNo(),
                String.format("货主不认可分拣损耗 %skg（%s），主张损耗率上限 %s。平台已向货主展示原始入库净重 %skg、分拣视频、坏果照片、等级划分与质检员签名，等待复核员重新抽样。",
                        sorting.getLossWeight(), percent(sorting.getLossRate()),
                        req.getOwnerClaimLossRate() == null ? "未填" : percent(req.getOwnerClaimLossRate()),
                        batch.getNetWeight())
                        + (req.getOwnerClaimNote() == null ? "" : " 货主说明：" + req.getOwnerClaimNote()),
                r.getReviewer());
        return r;
    }

    // ---------------- 重新抽样 ----------------

    @Transactional
    public ReviewSample addSample(Long reviewId, ReviewSampleRequest req) {
        LossReview r = mustGet(reviewId);
        if (r.getStatus() == ReviewStatus.CONCLUDED) {
            throw new BizException("复核单已结案，不能再抽样");
        }
        BigDecimal sampleW = nz(req.getSampleWeight());
        BigDecimal rotW = nz(req.getRotWeight());
        BigDecimal gA = nz(req.getGradeAWeight());
        BigDecimal gB = nz(req.getGradeBWeight());
        BigDecimal gP = nz(req.getGradeProcessWeight());
        if (sampleW.signum() <= 0) {
            throw new BizException("抽样重量必须大于 0");
        }
        if (rotW.add(gA).add(gB).add(gP).compareTo(sampleW) > 0) {
            throw new BizException("坏果与各等级重量合计不能超过抽样重量");
        }
        SortingRecord sorting = sortingRepository.findById(r.getSortingId()).orElseThrow();

        ReviewSample s = new ReviewSample();
        s.setReviewId(r.getId());
        s.setSampleSeq((int) sampleRepository.countByReviewId(r.getId()) + 1);
        s.setSamplePoint(req.getSamplePoint());
        s.setSampleWeight(sampleW);
        s.setRotWeight(rotW);
        s.setGradeAWeight(gA);
        s.setGradeBWeight(gB);
        s.setGradeProcessWeight(gP);
        s.setOperator(req.getOperator() == null ? r.getReviewer() : req.getOperator());
        s.setSampledAt(LocalDateTime.now());
        s.setRemark(req.getRemark());
        s = sampleRepository.save(s);

        // 汇总历次抽样推算全量口径
        List<ReviewSample> all = sampleRepository.findByReviewIdOrderBySampleSeqAsc(r.getId());
        BigDecimal totalSample = all.stream().map(ReviewSample::getSampleWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRot = all.stream().map(ReviewSample::getRotWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rotRate = totalSample.signum() == 0 ? BigDecimal.ZERO
                : totalRot.divide(totalSample, 4, RoundingMode.HALF_UP);
        BigDecimal reviewedLoss = sorting.getInputWeight().multiply(rotRate).setScale(2, RoundingMode.HALF_UP);
        reviewedLoss = reviewedLoss.min(sorting.getLossWeight()); // 复核损耗不超过登记损耗
        BigDecimal saleable = sorting.getInputWeight().subtract(reviewedLoss);

        r.setStatus(ReviewStatus.SAMPLING);
        r.setSampleWeight(totalSample);
        r.setSampleRotWeight(totalRot);
        r.setSampleRotRate(rotRate);
        r.setReviewedLossWeight(reviewedLoss);
        r.setReviewedSaleableWeight(saleable);
        r.setSampledAt(s.getSampledAt());
        reviewRepository.save(r);

        traceService.log(r.getBatchId(), null, null, Stage.LOSS_REVIEW,
                "复核重新抽样 #" + s.getSampleSeq() + " " + r.getReviewNo(),
                String.format("抽样点 %s，抽样 %skg，坏果 %skg，累计抽样坏果率 %s；按投入 %skg 推算复核损耗 %skg，调整后可售 %skg（原分拣口径损耗 %skg/可售 %skg）。",
                        s.getSamplePoint() == null ? "-" : s.getSamplePoint(), sampleW, rotW, percent(rotRate),
                        sorting.getInputWeight(), reviewedLoss, saleable,
                        sorting.getLossWeight(), sorting.getOutputWeight()),
                s.getOperator());
        return s;
    }

    // ---------------- 周转筐证据链 ----------------

    @Transactional
    public CrateHandlingRecord saveHandling(CrateHandlingRequest req) {
        CrateHandlingRecord h = new CrateHandlingRecord();
        if (req.getSortingId() != null) {
            SortingRecord sorting = sortingRepository.findById(req.getSortingId())
                    .orElseThrow(() -> new BizException("分拣单不存在"));
            h.setBatchId(sorting.getBatchId());
            h.setSortingId(sorting.getId());
        } else {
            h.setBatchId(req.getBatchId());
            h.setSortingId(req.getSortingId());
        }
        h.setCrateNo(req.getCrateNo());
        h.setStackLayer(req.getStackLayer());
        h.setStackHeight(req.getStackHeight());
        h.setVehicleNo(req.getVehicleNo());
        h.setSortStation(req.getSortStation());
        h.setArrivedAt(req.getArrivedAt());
        h.setUnloadedAt(req.getUnloadedAt());
        h.setReachedStationAt(req.getReachedStationAt());
        h.setSortingStartedAt(req.getSortingStartedAt());
        h.setHandler(req.getHandler());
        h.setRemark(req.getRemark());
        return crateHandlingRepository.save(h);
    }

    // ---------------- 结案 ----------------

    @Transactional
    public LossReview conclude(Long reviewId, ReviewConcludeRequest req) {
        LossReview r = mustGet(reviewId);
        if (r.getStatus() == ReviewStatus.CONCLUDED) {
            throw new BizException("复核单已结案");
        }
        List<ReviewSample> samples = sampleRepository.findByReviewIdOrderBySampleSeqAsc(r.getId());
        if (samples.isEmpty()) {
            throw new BizException("请先重新抽样后再给出复核结论");
        }
        if (req.getLossCause() == null) {
            throw new BizException("请选择损耗成因判定");
        }
        SortingRecord sorting = sortingRepository.findById(r.getSortingId()).orElseThrow();
        Batch batch = batchRepository.findById(r.getBatchId()).orElseThrow();

        // 1) 责任归属：到货质量/正常损耗→货主；装卸挤压/分拣台暴露→冷库；混合需显式指定
        Responsibility responsibility = resolveResponsibility(req);
        LossCause cause = req.getLossCause();

        // 2) 复核损耗与可售重量
        BigDecimal finalLoss = req.getReviewedLossWeight() != null
                ? req.getReviewedLossWeight()
                : nz(r.getReviewedLossWeight());
        finalLoss = finalLoss.max(BigDecimal.ZERO).min(sorting.getLossWeight());
        BigDecimal saleable = req.getReviewedSaleableWeight() != null
                ? req.getReviewedSaleableWeight()
                : sorting.getInputWeight().subtract(finalLoss);
        saleable = saleable.max(sorting.getOutputWeight())
                .min(sorting.getInputWeight());
        // 以可售重量反算最终损耗，保证两者口径一致
        finalLoss = sorting.getInputWeight().subtract(saleable).setScale(2, RoundingMode.HALF_UP);
        BigDecimal warehouseBorne = sorting.getLossWeight().subtract(finalLoss).max(BigDecimal.ZERO);

        // 3) 周转筐证据链：筐号 + 工位 + 搬运时间串联
        String crateAnalysis = handleCrates(r, sorting, req);
        boolean concentrated = req.getCrateConcentrated() != null
                ? req.getCrateConcentrated()
                : (req.getCrates() != null && !req.getCrates().isEmpty());

        // 4) 回写分拣单与批次重量口径
        sorting.setDisputeResolved(true);
        sorting.setAdjudicatedLossWeight(finalLoss);
        sorting.setReviewId(r.getId());
        sorting.setDisputeNote((sorting.getDisputeNote() == null ? "" : sorting.getDisputeNote())
                + "｜复核单 " + r.getReviewNo() + " 结论：" + cause.desc + "，"
                + responsibility.desc + "，复核损耗 " + finalLoss + "kg，冷库承担 " + warehouseBorne + "kg");
        sortingRepository.save(sorting);

        batch.setLossWeight(batch.getLossWeight().add(finalLoss));
        if (warehouseBorne.signum() > 0) {
            batch.setAcceptedWeight(batch.getAcceptedWeight().add(warehouseBorne));
        }
        batchRepository.save(batch);

        // 5) 冷库赔付记录（进入货主结算 + 分拣班组考核 + 客户赔付台账）
        CustomerCompensation compensation = null;
        BigDecimal avgPrice = avgSortPrice(sorting);
        BigDecimal compensateAmount = BigDecimal.ZERO;
        if (warehouseBorne.signum() > 0 && responsibility == Responsibility.WAREHOUSE) {
            compensation = new CustomerCompensation();
            compensation.setCompensationNo("P" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", compensationRepository.count() + 1));
            compensation.setReviewId(r.getId());
            compensation.setBatchId(batch.getId());
            compensation.setSortingId(sorting.getId());
            compensation.setOwnerId(batch.getOwnerId());
            compensation.setCustomerId(req.getCustomerId());
            compensation.setResponsibility(responsibility);
            compensation.setLossCause(cause);
            compensation.setWeight(warehouseBorne);
            compensation.setUnitPrice(avgPrice);
            compensateAmount = warehouseBorne.multiply(avgPrice).setScale(2, RoundingMode.HALF_UP);
            compensation.setAmount(compensateAmount);
            String station = sorting.getSortStation();
            compensation.setSortStation(station);
            compensation.setSortTeam(req.getSortTeam() != null ? req.getSortTeam()
                    : (station == null ? "分拣班组" : station + " 分拣班组"));
            compensation.setReason("争议复核 " + r.getReviewNo() + " 认定：" + cause.desc
                    + (crateAnalysis.isEmpty() ? "" : "；" + crateAnalysis));
            compensation.setHandler(r.getReviewer());
            compensation.setPaidAt(LocalDateTime.now());
            compensation.setBillWritten(false);
            compensation = compensationRepository.save(compensation);
        }

        // 6) 质检员考核回写：复核认定含到货质量成分，且重新抽样坏果率高于质检记录腐损率 → 漏检扣分
        Inspection inspection = inspectionRepository.findFirstByBatchIdOrderByInspectedAtDesc(batch.getId())
                .orElse(null);
        String inspectorName = inspection == null ? "未指派质检员" : inspection.getInspector();
        BigDecimal inspectRotRate = inspection == null ? BigDecimal.ZERO : nz(inspection.getRotRate());
        boolean arrivalComponent = cause == LossCause.ARRIVAL_QUALITY || cause == LossCause.MIXED;
        boolean missJudged = arrivalComponent
                && nz(r.getSampleRotRate()).compareTo(inspectRotRate) > 0
                && finalLoss.signum() > 0;
        int deduct = missJudged ? missJudgePoints : 0;
        Integer scoreAfter = writePerformance(r, batch, inspection, inspectorName, missJudged, deduct, cause);

        // 7) 复核单结案
        r.setStatus(ReviewStatus.CONCLUDED);
        r.setConcludedAt(LocalDateTime.now());
        r.setLossCause(cause);
        r.setResponsibility(responsibility);
        r.setReviewedLossWeight(finalLoss);
        r.setReviewedSaleableWeight(saleable);
        r.setWarehouseBorneWeight(warehouseBorne);
        r.setCompensateAmount(compensateAmount);
        r.setCrateConcentrated(concentrated);
        r.setCrateAnalysis(crateAnalysis);
        r.setConclusion(req.getConclusion());
        r.setReviewerSignature(req.getReviewerSignature() == null ? r.getReviewer() : req.getReviewerSignature());
        r.setWrittenBack(true);
        r.setPerformanceWritten(true);
        r.setInspectorName(inspectorName);
        r.setInspectorDeductedPoints(deduct);
        if (compensation != null) {
            r.setCustomerCompensationId(compensation.getId());
        }
        reviewRepository.save(r);

        // 8) 告警闭环
        String resolution = "复核单 " + r.getReviewNo() + " 结案：" + cause.desc + "，" + responsibility.desc;
        alertRepository.findByBatchIdOrderByCreatedAtDesc(batch.getId()).stream()
                .filter(a -> !a.getResolved() && a.getType() == AlertType.LOSS_DISPUTE)
                .forEach(a -> alertRepository.save(resolveAlert(a, resolution)));

        traceService.log(batch.getId(), null, null, Stage.LOSS_REVIEW,
                "损耗争议复核结案 " + r.getReviewNo(),
                String.format("重新抽样坏果率 %s，复核损耗 %skg（原登记 %skg），调整后可售 %skg；成因：%s；责任：%s；冷库赔付 %s kg × %s 元 = %s 元；质检员 %s %s（考核后分值 %s）。%s",
                        percent(r.getSampleRotRate()), finalLoss, sorting.getLossWeight(), saleable,
                        cause.desc, responsibility.desc, warehouseBorne, avgPrice, compensateAmount,
                        inspectorName, missJudged ? "到货质量漏检扣 " + deduct + " 分" : "不扣分",
                        scoreAfter, crateAnalysis.isEmpty() ? "" : "【周转筐证据链】" + crateAnalysis),
                r.getReviewer());
        return r;
    }

    private com.county.coldchain.domain.entity.Alert resolveAlert(com.county.coldchain.domain.entity.Alert a,
                                                                  String resolution) {
        a.setResolved(true);
        a.setResolution(resolution);
        return a;
    }

    private Responsibility resolveResponsibility(ReviewConcludeRequest req) {
        if (req.getResponsibility() != null && !req.getResponsibility().isBlank()) {
            return Responsibility.valueOf(req.getResponsibility());
        }
        return switch (req.getLossCause()) {
            case ARRIVAL_QUALITY, NORMAL -> Responsibility.OWNER;
            case LOADING_SQUEEZE, SORT_EXPOSURE -> Responsibility.WAREHOUSE;
            case MIXED -> throw new BizException("多因混合结论必须显指定责任方（货主/冷库）");
        };
    }

    /** 把筐号、分拣工位、搬运时间连到一起，逐筐给出来货质量/装卸挤压/分拣台暴露的判定。 */
    private String handleCrates(LossReview r, SortingRecord sorting, ReviewConcludeRequest req) {
        if (req.getCrates() == null || req.getCrates().isEmpty()) {
            // 即使没有手工录入集中筐，也尝试根据坏果照片关联已有搬运记录给出提示
            return "";
        }
        StringBuilder analysis = new StringBuilder();
        for (ReviewConcludeRequest.CrateItem c : req.getCrates()) {
            CrateEvidence e = new CrateEvidence();
            e.setReviewId(r.getId());
            e.setBatchId(r.getBatchId());
            e.setCrateNo(c.getCrateNo());
            e.setBadFruitWeight(nz(c.getBadFruitWeight()));
            e.setSortStation(c.getSortStation() == null ? sorting.getSortStation() : c.getSortStation());
            e.setDamagePattern(c.getDamagePattern());
            e.setRemark(c.getRemark());

            CrateHandlingRecord h = crateHandlingRepository
                    .findFirstByBatchIdAndCrateNoOrderByArrivedAtAsc(r.getBatchId(), c.getCrateNo())
                    .orElse(null);
            Integer exposed = null;
            LossCause indicated = c.getIndicatedCause();
            if (h != null) {
                e.setHandlingId(h.getId());
                e.setArrivedAt(h.getArrivedAt());
                e.setUnloadedAt(h.getUnloadedAt());
                e.setReachedStationAt(h.getReachedStationAt());
                if (h.getReachedStationAt() != null && h.getSortingStartedAt() != null) {
                    exposed = (int) Duration.between(h.getReachedStationAt(), h.getSortingStartedAt()).toMinutes();
                    exposed = Math.max(0, exposed);
                    e.setExposedMinutes(exposed);
                }
                if (indicated == null) {
                    indicated = inferCause(h, exposed, c.getDamagePattern(), req.getLossCause());
                }
                if (e.getSortStation() == null) {
                    e.setSortStation(h.getSortStation());
                }
            }
            if (indicated == null) {
                indicated = req.getLossCause() == LossCause.MIXED ? LossCause.ARRIVAL_QUALITY : req.getLossCause();
            }
            e.setIndicatedCause(indicated);
            crateEvidenceRepository.save(e);

            analysis.append("筐 ").append(c.getCrateNo())
                    .append("（").append(e.getSortStation() == null ? "工位待查" : e.getSortStation()).append("）")
                    .append("坏果 ").append(e.getBadFruitWeight()).append("kg，");
            if (h != null) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("MM-dd HH:mm");
                analysis.append("到货 ").append(fmt(h.getArrivedAt(), f))
                        .append("→卸车 ").append(fmt(h.getUnloadedAt(), f))
                        .append("→到台 ").append(fmt(h.getReachedStationAt(), f))
                        .append("→开拣 ").append(fmt(h.getSortingStartedAt(), f));
                if (h.getStackLayer() != null) {
                    analysis.append("，堆层第").append(h.getStackLayer()).append("层");
                }
                if (h.getStackHeight() != null) {
                    analysis.append("，码放").append(h.getStackHeight()).append("层高");
                }
                if (exposed != null) {
                    analysis.append("，分拣台暴露 ").append(exposed).append(" 分钟");
                }
                analysis.append("，判定：").append(indicated.desc);
            } else {
                analysis.append("无搬运时间线记录，按坏果形态/复核结论判定：").append(indicated.desc);
            }
            analysis.append("；");
        }
        return analysis.toString();
    }

    private LossCause inferCause(CrateHandlingRecord h, Integer exposedMinutes, String pattern, LossCause fallback) {
        boolean squeezePattern = pattern != null && (pattern.contains("挤压") || pattern.contains("压伤") || pattern.contains("破碎"));
        boolean bottomOrHigh = (h.getStackLayer() != null && h.getStackLayer() <= 1)
                || (h.getStackHeight() != null && h.getStackHeight() >= squeezeStackHeight);
        if (exposedMinutes != null && exposedMinutes >= exposureWarnMinutes) {
            return LossCause.SORT_EXPOSURE;
        }
        if (squeezePattern && bottomOrHigh) {
            return LossCause.LOADING_SQUEEZE;
        }
        if (exposedMinutes != null && exposedMinutes >= exposureWarnMinutes) {
            return LossCause.SORT_EXPOSURE;
        }
        if (squeezePattern) {
            return LossCause.LOADING_SQUEEZE;
        }
        return fallback == null || fallback == LossCause.MIXED ? LossCause.ARRIVAL_QUALITY : fallback;
    }

    private Integer writePerformance(LossReview r, Batch batch, Inspection inspection, String inspectorName,
                                     boolean missJudged, int deduct, LossCause cause) {
        int already = performanceRepository.findByInspectorOrderByOccurredAtDesc(inspectorName).stream()
                .map(InspectorPerformance::getDeductedPoints)
                .filter(java.util.Objects::nonNull)
                .reduce(0, Integer::sum);
        BigDecimal inspectRotRate = inspection == null || inspection.getRotRate() == null
                ? BigDecimal.ZERO : inspection.getRotRate();
        InspectorPerformance p = new InspectorPerformance();
        p.setInspector(inspectorName);
        p.setBatchId(batch.getId());
        p.setReviewId(r.getId());
        p.setReviewNo(r.getReviewNo());
        p.setInspectionId(inspection == null ? null : inspection.getId());
        p.setTitle("分拣损耗争议复核 " + r.getReviewNo());
        p.setDeductedPoints(deduct);
        p.setScoreAfter(100 - already - deduct);
        p.setMissJudged(missJudged);
        p.setDetail(String.format("批次 %s 复核成因为%s：%s。",
                batch.getBatchNo(), cause.desc,
                missJudged ? String.format(
                        "抽样坏果率 %s 高于质检记录腐损率 %s，到货质量问题验收时未充分识别，扣 %d 分",
                        percent(r.getSampleRotRate()), percent(inspectRotRate), deduct)
                        : "质检判定与复核结论一致，不扣分"));
        p.setOccurredAt(LocalDateTime.now());
        performanceRepository.save(p);
        return p.getScoreAfter();
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<LossReview> list(String status) {
        if (status == null || status.isBlank()) {
            return reviewRepository.findAllByOrderByCreatedAtDesc();
        }
        if ("OPEN".equals(status)) {
            return reviewRepository.findByStatusInOrderByCreatedAtDesc(
                    List.of(ReviewStatus.OPEN, ReviewStatus.SAMPLING));
        }
        return reviewRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(ReviewStatus.valueOf(status)));
    }

    @Transactional(readOnly = true)
    public LossReview mustGet(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> new BizException("复核单不存在: " + id));
    }

    /** 复核证据包：平台向货主/复核员展示的全部原始证据与复核过程数据。 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        LossReview r = mustGet(id);
        SortingRecord sorting = sortingRepository.findById(r.getSortingId()).orElseThrow();
        Batch batch = batchRepository.findById(r.getBatchId()).orElseThrow();
        List<SortItem> items = sortItemRepository.findBySortingId(sorting.getId());
        Inspection inspection = inspectionRepository.findFirstByBatchIdOrderByInspectedAtDesc(r.getBatchId())
                .orElse(null);
        List<ReviewSample> samples = sampleRepository.findByReviewIdOrderBySampleSeqAsc(r.getId());
        List<CrateEvidence> crates = crateEvidenceRepository.findByReviewIdOrderByBadFruitWeightDesc(r.getId());
        List<CrateHandlingRecord> handling = crateHandlingRepository.findBySortingIdOrderByArrivedAtAsc(sorting.getId());
        CustomerCompensation compensation = r.getCustomerCompensationId() == null ? null
                : compensationRepository.findById(r.getCustomerCompensationId()).orElse(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("review", r);
        m.put("batch", batch);
        m.put("sorting", sorting);
        m.put("gradeItems", items);
        m.put("inspection", inspection);
        m.put("samples", samples);
        m.put("crateEvidences", crates);
        m.put("crateHandling", handling);
        m.put("compensation", compensation);
        m.put("avgSortPrice", avgSortPrice(sorting));
        return m;
    }

    @Transactional(readOnly = true)
    public List<CrateHandlingRecord> handlingByBatch(Long batchId) {
        return crateHandlingRepository.findByBatchIdOrderByArrivedAtAsc(batchId);
    }

    @Transactional(readOnly = true)
    public List<InspectorPerformance> performanceList() {
        return performanceRepository.findAllByOrderByOccurredAtDesc();
    }

    /** 质检员考核汇总：分值 = 100 - 累计扣分。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> performanceSummary() {
        Map<String, int[]> agg = new LinkedHashMap<>(); // [reviewCount, missCount, deduct]
        Map<String, Integer> idx = new LinkedHashMap<>();
        List<InspectorPerformance> all = performanceRepository.findAllByOrderByOccurredAtDesc();
        for (InspectorPerformance p : all) {
            int[] a = agg.computeIfAbsent(p.getInspector(), k -> new int[3]);
            a[0]++;
            if (Boolean.TRUE.equals(p.getMissJudged())) {
                a[1]++;
            }
            a[2] += p.getDeductedPoints() == null ? 0 : p.getDeductedPoints();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inspector", e.getKey());
            m.put("reviewCount", e.getValue()[0]);
            m.put("missJudgeCount", e.getValue()[1]);
            m.put("deductedPoints", e.getValue()[2]);
            m.put("score", 100 - e.getValue()[2]);
            out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CustomerCompensation> compensationList() {
        return compensationRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 分拣班组考核汇总：按责任班组统计冷库责任件数、承担重量与赔付金额。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teamSummary() {
        Map<String, double[]> agg = new LinkedHashMap<>(); // [件数, 重量kg, 赔付金额元]
        for (CustomerCompensation c : compensationRepository.findAllByOrderByCreatedAtDesc()) {
            String team = c.getSortTeam() == null || c.getSortTeam().isBlank() ? "分拣班组" : c.getSortTeam();
            double[] a = agg.computeIfAbsent(team, k -> new double[3]);
            a[0] += 1;
            a[1] += nz(c.getWeight()).doubleValue();
            a[2] += nz(c.getAmount()).doubleValue();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : agg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sortTeam", e.getKey());
            m.put("caseCount", (int) e.getValue()[0]);
            m.put("borneWeight", BigDecimal.valueOf(e.getValue()[1]).setScale(2, RoundingMode.HALF_UP));
            m.put("compensateAmount", BigDecimal.valueOf(e.getValue()[2]).setScale(2, RoundingMode.HALF_UP));
            out.add(m);
        }
        return out;
    }

    private BigDecimal avgSortPrice(SortingRecord sorting) {
        List<SortItem> items = sortItemRepository.findBySortingId(sorting.getId());
        BigDecimal w = BigDecimal.ZERO;
        BigDecimal v = BigDecimal.ZERO;
        for (SortItem i : items) {
            w = w.add(i.getWeight());
            v = v.add(i.getWeight().multiply(nz(i.getUnitPrice())));
        }
        if (w.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return v.divide(w, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String percent(BigDecimal v) {
        if (v == null) return "-";
        return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }
    private String fmt(LocalDateTime t, DateTimeFormatter f) {
        return t == null ? "-" : t.format(f);
    }
}
