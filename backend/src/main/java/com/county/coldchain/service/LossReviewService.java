package com.county.coldchain.service;

import com.county.coldchain.domain.DomainEnums.AlertLevel;
import com.county.coldchain.domain.DomainEnums.AlertType;
import com.county.coldchain.domain.DomainEnums.AssessmentResult;
import com.county.coldchain.domain.DomainEnums.AssessmentTarget;
import com.county.coldchain.domain.DomainEnums.BasketCause;
import com.county.coldchain.domain.DomainEnums.ReviewConclusion;
import com.county.coldchain.domain.DomainEnums.ReviewStatus;
import com.county.coldchain.domain.DomainEnums.Stage;
import com.county.coldchain.domain.entity.Alert;
import com.county.coldchain.domain.entity.Batch;
import com.county.coldchain.domain.entity.CustomerCompensation;
import com.county.coldchain.domain.entity.Inspection;
import com.county.coldchain.domain.entity.LossReview;
import com.county.coldchain.domain.entity.ReviewBasketClue;
import com.county.coldchain.domain.entity.SortItem;
import com.county.coldchain.domain.entity.SortingAssessment;
import com.county.coldchain.domain.entity.SortingRecord;
import com.county.coldchain.repo.AlertRepository;
import com.county.coldchain.repo.BatchRepository;
import com.county.coldchain.repo.CustomerCompensationRepository;
import com.county.coldchain.repo.InspectionRepository;
import com.county.coldchain.repo.LossReviewRepository;
import com.county.coldchain.repo.ReviewBasketClueRepository;
import com.county.coldchain.repo.SortItemRepository;
import com.county.coldchain.repo.SortingAssessmentRepository;
import com.county.coldchain.repo.SortingRecordRepository;
import com.county.coldchain.support.BizException;
import com.county.coldchain.web.dto.BasketClueRequest;
import com.county.coldchain.web.dto.ReviewApplyRequest;
import com.county.coldchain.web.dto.ReviewConclusionRequest;
import com.county.coldchain.web.dto.ReviewSampleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分拣损耗争议复核。
 * 货主认为分拣后损耗比例过高 → 平台固定证据快照（原始入库重量/分拣视频/坏果照片/等级划分/质检员签名）
 * → 复核员重新抽样、登记周转筐线索（筐号+工位+搬运时间）→ 出具结论：
 * 到货质量差货主承担；装卸挤压 / 分拣台长时间暴露由冷库赔付。
 * 结论回写：分拣单与批次可售重量、货主结算账单、质检员/班组考核、客户赔付记录、追溯链与告警。
 */
@Service
@RequiredArgsConstructor
public class LossReviewService {

    /** 分拣台暴露时长阈值（分钟）：超过即判定为"长时间暴露" */
    static final long EXPOSURE_LIMIT_MINUTES = 30;

    private final LossReviewRepository reviewRepository;
    private final ReviewBasketClueRepository basketRepository;
    private final SortingAssessmentRepository assessmentRepository;
    private final CustomerCompensationRepository compensationRepository;
    private final SortingRecordRepository sortingRepository;
    private final SortItemRepository sortItemRepository;
    private final BatchRepository batchRepository;
    private final InspectionRepository inspectionRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final TraceService traceService;

    // ---------------- 发起复核（固定证据快照） ----------------

    @Transactional
    public LossReview apply(ReviewApplyRequest req) {
        SortingRecord rec = sortingRepository.findById(req.getSortingId())
                .orElseThrow(() -> new BizException("分拣单不存在"));
        Batch batch = batchRepository.findById(rec.getBatchId())
                .orElseThrow(() -> new BizException("批次不存在"));
        reviewRepository.findFirstBySortingIdAndStatusNot(rec.getId(), ReviewStatus.CANCELLED)
                .ifPresent(r -> {
                    throw new BizException("该分拣单已存在复核单 " + r.getReviewNo()
                            + "（" + r.getStatus().desc + "），不能重复发起");
                });

        Inspection latestQc = inspectionRepository
                .findFirstByBatchIdOrderByInspectedAtDesc(batch.getId()).orElse(null);

        LossReview rv = new LossReview();
        rv.setReviewNo("R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", reviewRepository.count() + 1));
        rv.setBatchId(batch.getId());
        rv.setSortingId(rec.getId());
        rv.setOwnerId(batch.getOwnerId());
        rv.setApplicant(req.getApplicant() == null ? "货主" : req.getApplicant());
        rv.setAppliedAt(LocalDateTime.now());
        rv.setStatus(ReviewStatus.OPEN);
        rv.setClaimedLossRate(req.getClaimedLossRate());
        rv.setClaimReason(req.getClaimReason());

        // ---- 平台向复核人员展示的证据快照（不可篡改） ----
        rv.setOriginalNetWeight(batch.getNetWeight());
        rv.setSortingInputWeight(rec.getInputWeight());
        rv.setSortingOutputWeight(rec.getOutputWeight());
        rv.setSortingLossWeight(rec.getLossWeight());
        rv.setSortingLossRate(rec.getLossRate());
        rv.setSortingVideo(req.getSortingVideo() == null || req.getSortingVideo().isBlank()
                ? "VIDEO-" + rec.getSortingNo() + "-01（分拣台全程监控，平台可调阅）"
                : req.getSortingVideo());
        rv.setBadFruitPhotos(req.getBadFruitPhotos() == null || req.getBadFruitPhotos().isBlank()
                ? "PHOTO-" + rec.getSortingNo() + "-01~06（坏果特写 6 张，平台可调阅）"
                : req.getBadFruitPhotos());
        rv.setGradeBreakdown(buildGradeBreakdown(rec.getId()));
        rv.setQcSignature(latestQc == null ? "（无质检记录）" : latestQc.getInspector());
        rv.setQcEvidenceNote(req.getQcEvidenceNote() == null
                ? (latestQc == null ? "" : "质检验收：" + nz(latestQc.getRemark()))
                : req.getQcEvidenceNote());
        rv = reviewRepository.save(rv);

        alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.CRITICAL, batch.getId(), null,
                "分拣损耗争议复核 " + rv.getReviewNo(),
                String.format("货主不认可分拣损耗 %skg（%s），主张 ≤ %s。证据已快照（原重 %skg/视频/坏果照片/等级划分/质检员 %s 签名），等待复核员重新抽样。",
                        rec.getLossWeight(), percent(rec.getLossRate()),
                        req.getClaimedLossRate() == null ? "另行核定" : percent(req.getClaimedLossRate()),
                        batch.getNetWeight(), rv.getQcSignature()),
                rv.getApplicant());
        traceService.log(batch.getId(), Stage.SORTING,
                "货主发起损耗争议复核 " + rv.getReviewNo(),
                "分拣单 " + rec.getSortingNo() + " 登记损耗 " + rec.getLossWeight() + "kg（"
                        + percent(rec.getLossRate()) + "）。平台固定证据：原始入库净重 "
                        + batch.getNetWeight() + "kg、分拣视频、坏果照片、等级划分、质检员签名。"
                        + (req.getClaimReason() == null ? "" : "货主主张：" + req.getClaimReason()),
                rv.getApplicant());
        return rv;
    }

    // ---------------- 周转筐线索 ----------------

    @Transactional
    public ReviewBasketClue addBasket(Long reviewId, BasketClueRequest req) {
        LossReview rv = mustGet(reviewId);
        ensureOpen(rv);
        ReviewBasketClue clue = new ReviewBasketClue();
        clue.setReviewId(reviewId);
        clue.setBasketNo(req.getBasketNo());
        clue.setWorkstation(req.getWorkstation());
        clue.setSortStationAt(req.getSortStationAt());
        clue.setMovedAwayAt(req.getMovedAwayAt());
        clue.setMover(req.getMover());
        clue.setMoveStage(req.getMoveStage());
        clue.setBadRate(req.getBadRate() == null ? BigDecimal.ZERO : req.getBadRate());
        clue.setCause(req.getCause() == null || req.getCause().isBlank()
                ? suggestCause(req) : BasketCause.valueOf(req.getCause()));
        clue.setCauseBasis(req.getCauseBasis() == null ? buildBasis(req, clue.getCause())
                : req.getCauseBasis() + "｜" + buildBasis(req, clue.getCause()));
        clue = basketRepository.save(clue);
        traceService.log(rv.getBatchId(), Stage.SORTING,
                "复核登记坏果周转筐 " + clue.getBasketNo(),
                String.format("筐号 %s，分拣工位 %s，搬运环节 %s（%s），台面时段 %s ~ %s，筐内坏果率 %s。串联判定：%s。",
                        clue.getBasketNo(), nz(clue.getWorkstation()), nz(clue.getMoveStage()),
                        nz(clue.getMover()), fmt(req.getSortStationAt()), fmt(req.getMovedAwayAt()),
                        percent(clue.getBadRate()), clue.getCause().desc),
                "复核员");
        return clue;
    }

    /** 筐号 + 工位 + 搬运时间 → 原因建议：超时暴露/搬运挤压/来货质量。 */
    private BasketCause suggestCause(BasketClueRequest req) {
        long exposure = exposureMinutes(req);
        if (exposure >= EXPOSURE_LIMIT_MINUTES) {
            return BasketCause.SORT_EXPOSURE;
        }
        String stage = req.getMoveStage() == null ? "" : req.getMoveStage();
        if (stage.contains("卸车") || stage.contains("搬运") || stage.contains("装车")) {
            return BasketCause.LOADING_SQUEEZE;
        }
        return BasketCause.ARRIVAL_QUALITY;
    }

    private String buildBasis(BasketClueRequest req, BasketCause cause) {
        long mins = exposureMinutes(req);
        return switch (cause) {
            case SORT_EXPOSURE -> "台面暴露 " + mins + " 分钟 ≥ " + EXPOSURE_LIMIT_MINUTES
                    + " 分钟，失水/萎蔫风险高，判定分拣台长时间暴露";
            case LOADING_SQUEEZE -> "台面暴露仅 " + mins + " 分钟，坏果出现在"
                    + nz(req.getMoveStage()) + "环节（" + nz(req.getMover()) + "），挤压/堆码痕迹，判定装卸挤压";
            case INCLUSIVE -> "同时存在来货坏果、挤压与暴露特征，判定混合原因";
            case ARRIVAL_QUALITY -> "台面暴露 " + mins + " 分钟且无明显挤压，坏果上台即存在，判定来货质量差";
        };
    }

    private long exposureMinutes(BasketClueRequest req) {
        if (req.getSortStationAt() == null || req.getMovedAwayAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(req.getSortStationAt(), req.getMovedAwayAt()).toMinutes());
    }

    // ---------------- 重新抽样 ----------------

    @Transactional
    public LossReview sample(Long reviewId, ReviewSampleRequest req) {
        LossReview rv = mustGet(reviewId);
        ensureOpen(rv);
        if (req.getSampleWeight() == null || req.getSampleWeight().signum() <= 0) {
            throw new BizException("抽样重量必须大于 0");
        }
        if (req.getSampleBadWeight().signum() < 0
                || req.getSampleBadWeight().compareTo(req.getSampleWeight()) > 0) {
            throw new BizException("抽样坏果重量需在 0 与抽样重量之间");
        }
        rv.setSampleWeight(req.getSampleWeight());
        rv.setSampleBadWeight(req.getSampleBadWeight());
        rv.setSampleBadRate(req.getSampleBadWeight().divide(req.getSampleWeight(), 4, RoundingMode.HALF_UP));
        rv.setSampler(req.getSampler() == null ? "复核员" : req.getSampler());
        rv.setSampledAt(LocalDateTime.now());
        rv.setSampleNote(req.getSampleNote());
        rv.setStatus(ReviewStatus.SAMPLING);
        reviewRepository.save(rv);
        traceService.log(rv.getBatchId(), Stage.SORTING,
                "复核重新抽样 " + rv.getReviewNo(),
                String.format("抽样 %skg，坏果 %skg，抽样坏果率 %s（分拣登记损耗率 %s）。%s",
                        rv.getSampleWeight(), rv.getSampleBadWeight(), percent(rv.getSampleBadRate()),
                        percent(rv.getSortingLossRate()), nz(rv.getSampleNote())),
                rv.getSampler());
        return rv;
    }

    // ---------------- 出具结论 ----------------

    @Transactional
    public LossReview conclude(Long reviewId, ReviewConclusionRequest req) {
        LossReview rv = mustGet(reviewId);
        ensureOpen(rv);
        SortingRecord rec = sortingRepository.findById(rv.getSortingId()).orElseThrow();
        Batch batch = batchRepository.findById(rv.getBatchId()).orElseThrow();
        ReviewConclusion conclusion = ReviewConclusion.valueOf(req.getConclusion());
        BigDecimal confirmedLoss = req.getConfirmedLossWeight();
        if (confirmedLoss.signum() < 0 || confirmedLoss.compareTo(rec.getLossWeight()) > 0) {
            throw new BizException("复核确认损耗需在 0 ~ 登记损耗 " + rec.getLossWeight() + "kg 之间");
        }

        // 1) 责任重量拆分
        BigDecimal ownerBorne;
        BigDecimal warehouseBorne;
        switch (conclusion) {
            case OWNER_QUALITY -> {
                ownerBorne = confirmedLoss;
                warehouseBorne = BigDecimal.ZERO;
            }
            case LOADING_SQUEEZE, SORT_EXPOSURE -> {
                ownerBorne = BigDecimal.ZERO;
                warehouseBorne = confirmedLoss;
            }
            default -> {
                ownerBorne = nz(req.getOwnerBorneWeight());
                warehouseBorne = nz(req.getWarehouseBorneWeight());
                if (ownerBorne.add(warehouseBorne).compareTo(confirmedLoss) != 0) {
                    throw new BizException("MIXED 结论下货主承担 + 冷库承担须等于确认损耗 " + confirmedLoss + "kg");
                }
            }
        }

        // 2) 周转筐线索（随结论提交则整体替换）
        if (req.getBaskets() != null && !req.getBaskets().isEmpty()) {
            basketRepository.findByReviewIdOrderBySortStationAtAsc(reviewId)
                    .forEach(basketRepository::delete);
            for (ReviewConclusionRequest.BasketClueDto b : req.getBaskets()) {
                BasketClueRequest br = new BasketClueRequest();
                br.setBasketNo(b.getBasketNo());
                br.setWorkstation(b.getWorkstation());
                br.setSortStationAt(b.getSortStationAt());
                br.setMovedAwayAt(b.getMovedAwayAt());
                br.setMover(b.getMover());
                br.setMoveStage(b.getMoveStage());
                br.setBadRate(b.getBadRate());
                br.setCause(b.getCause());
                br.setCauseBasis(b.getCauseBasis());
                addBasket(reviewId, br);
            }
        }
        List<ReviewBasketClue> clues = basketRepository.findByReviewIdOrderBySortStationAtAsc(reviewId);

        // 3) 赔付单价 / 金额
        BigDecimal price = req.getCompensatePrice() != null && req.getCompensatePrice().signum() > 0
                ? req.getCompensatePrice() : avgSortPrice(rec.getId());
        BigDecimal compAmount = warehouseBorne.multiply(price).setScale(2, RoundingMode.HALF_UP);

        // 4) 回写分拣单（旧账单争议口径以此为准，避免重复赔付）
        BigDecimal prevAdj = rec.getAdjudicatedLossWeight();
        rec.setDisputeResolved(true);
        rec.setAdjudicatedLossWeight(confirmedLoss);
        rec.setDisputeNote((rec.getDisputeNote() == null ? "" : rec.getDisputeNote())
                + "｜复核单 " + rv.getReviewNo() + " 结论：" + conclusion.desc
                + "，确认损耗 " + confirmedLoss + "kg（货主 " + ownerBorne + "/冷库 " + warehouseBorne
                + "），冷库赔付 " + compAmount + " 元");
        sortingRepository.save(rec);

        // 5) 回写批次损耗 / 可售重量（兼容旧争议裁定与无争议分拣，幂等）
        BigDecimal prevLossOnBatch = (!Boolean.TRUE.equals(rec.getDisputed()) || prevAdj != null)
                ? (prevAdj != null ? prevAdj : rec.getLossWeight())
                : BigDecimal.ZERO;
        BigDecimal prevAcceptedBack = (prevAdj != null)
                ? rec.getLossWeight().subtract(prevAdj) : BigDecimal.ZERO;
        BigDecimal newBack = rec.getLossWeight().subtract(confirmedLoss);
        batch.setLossWeight(nz(batch.getLossWeight()).add(confirmedLoss).subtract(prevLossOnBatch)
                .max(BigDecimal.ZERO));
        batch.setAcceptedWeight(nz(batch.getAcceptedWeight()).add(newBack).subtract(prevAcceptedBack));
        batchRepository.save(batch);

        // 6) 客户赔付记录
        boolean needCustomerComp = Boolean.TRUE.equals(req.getCustomerCompNeeded());
        String compNo = null;
        if (needCustomerComp) {
            CustomerCompensation cc = new CustomerCompensation();
            compNo = "P" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", compensationRepository.count() + 1);
            cc.setCompensationNo(compNo);
            cc.setReviewId(rv.getId());
            cc.setBatchId(batch.getId());
            cc.setCustomerId(req.getCustomerId());
            cc.setWeight(warehouseBorne);
            cc.setAmount(nz(req.getCustomerCompAmount()));
            cc.setReason(reviewId + " 复核：" + conclusion.desc + "，坏果随货流向客户"
                    + (req.getCustomerCompNote() == null ? "" : "；" + req.getCustomerCompNote()));
            cc.setOperator(req.getReviewer());
            cc.setCompensatedAt(LocalDateTime.now());
            cc.setStatus("待赔付");
            compensationRepository.save(cc);
        }

        // 7) 回写质检员 / 班组考核
        buildAssessments(rv, rec, batch, conclusion, clues, warehouseBorne, compAmount,
                req.getReviewer());

        // 8) 复核单落账
        rv.setStatus(ReviewStatus.CONFIRMED);
        rv.setConclusion(conclusion);
        rv.setConfirmedLossWeight(confirmedLoss);
        rv.setOwnerBorneWeight(ownerBorne);
        rv.setWarehouseBorneWeight(warehouseBorne);
        rv.setSalableAdjustWeight(newBack);
        rv.setCompensatePrice(price);
        rv.setCompensateAmount(compAmount);
        rv.setCustomerCompNeeded(needCustomerComp);
        rv.setCustomerCompAmount(needCustomerComp ? nz(req.getCustomerCompAmount()) : BigDecimal.ZERO);
        rv.setCustomerCompNote(needCustomerComp ? req.getCustomerCompNote() : null);
        rv.setReviewer(req.getReviewer());
        rv.setConfirmedAt(LocalDateTime.now());
        rv.setConclusionNote(req.getConclusionNote());
        reviewRepository.save(rv);

        // 9) 解决该批次未处理的分拣损耗争议告警
        for (Alert a : alertRepository.findByBatchIdOrderByCreatedAtDesc(batch.getId())) {
            if (!a.getResolved() && a.getType() == AlertType.LOSS_DISPUTE) {
                alertService.resolve(a.getId(),
                        "复核单 " + rv.getReviewNo() + " 结论：" + conclusion.desc
                                + "，货主承担 " + ownerBorne + "kg，冷库承担 " + warehouseBorne
                                + "kg，赔付 " + compAmount + " 元");
            }
        }

        traceService.log(batch.getId(), Stage.SORTING,
                "损耗争议复核结论 " + rv.getReviewNo(),
                String.format("重新抽样坏果率 %s；周转筐线索 %s 个。结论【%s】：确认损耗 %skg，货主承担 %skg，冷库承担 %skg，"
                                + "可售重量回补 %skg，冷库按 %s 元/kg 赔付 %s 元%s。结论已回写结算账单口径、质检员/班组考核%s。",
                        percent(rv.getSampleBadRate()), clues.size(), conclusion.desc,
                        confirmedLoss, ownerBorne, warehouseBorne, newBack, price, compAmount,
                        needCustomerComp ? "，并登记客户赔付记录 " + compNo : "",
                        needCustomerComp ? "与客户赔付记录" : ""),
                req.getReviewer());
        return rv;
    }

    /** 按结论与筐线索回写考核台账。 */
    private void buildAssessments(LossReview rv, SortingRecord rec, Batch batch,
                                  ReviewConclusion conclusion, List<ReviewBasketClue> clues,
                                  BigDecimal warehouseBorne, BigDecimal compAmount, String reviewer) {
        Inspection qc = inspectionRepository.findFirstByBatchIdOrderByInspectedAtDesc(batch.getId())
                .orElse(null);
        String qcName = rv.getQcSignature();
        String sortTeam = rec.getSorter() + "（分拣班组）";
        String loadingTeam = clues.stream().map(ReviewBasketClue::getMover)
                .filter(s -> s != null && !s.isBlank()).findFirst().orElse("装卸班组");

        switch (conclusion) {
            case OWNER_QUALITY -> assessment(rv, batch, AssessmentTarget.QC_INSPECTOR, qcName,
                    qcMissedArrivalQuality(qc) ? AssessmentResult.DEDUCT : AssessmentResult.NEUTRAL,
                    qcMissedArrivalQuality(qc) ? new BigDecimal("5") : BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "来货质量差导致损耗，货主承担；" + (qcMissedArrivalQuality(qc)
                            ? "质检腐损率记录偏低、未预警来货质量风险，记考核扣分"
                            : "质检已记录腐损情况，质检员无责"), reviewer);
            case LOADING_SQUEEZE -> {
                assessment(rv, batch, AssessmentTarget.LOADING_TEAM, loadingTeam,
                        AssessmentResult.DEDUCT, new BigDecimal("10"), BigDecimal.ZERO,
                        "装卸挤压造成坏果集中（冷库承担 " + warehouseBorne + "kg、赔付 "
                                + compAmount + " 元），列入装卸班组考核", reviewer);
                assessment(rv, batch, AssessmentTarget.QC_INSPECTOR, qcName,
                        AssessmentResult.NEUTRAL, BigDecimal.ZERO, BigDecimal.ZERO,
                        "坏果主因装卸挤压，与到货质检结论无直接关联，质检员无责", reviewer);
            }
            case SORT_EXPOSURE -> assessment(rv, batch, AssessmentTarget.SORT_TEAM, sortTeam,
                    AssessmentResult.DEDUCT, new BigDecimal("10"), BigDecimal.ZERO,
                    "分拣台长时间暴露（≥" + EXPOSURE_LIMIT_MINUTES + " 分钟）导致坏果，冷库承担 "
                            + warehouseBorne + "kg、赔付 " + compAmount + " 元，列入分拣班组考核",
                    reviewer);
            case MIXED -> {
                if (clues.stream().anyMatch(c -> c.getCause() == BasketCause.LOADING_SQUEEZE
                        || c.getCause() == BasketCause.INCLUSIVE)) {
                    assessment(rv, batch, AssessmentTarget.LOADING_TEAM, loadingTeam,
                            AssessmentResult.DEDUCT, new BigDecimal("5"), BigDecimal.ZERO,
                            "混合原因中部分坏果来自装卸挤压，列入装卸班组考核", reviewer);
                }
                if (clues.stream().anyMatch(c -> c.getCause() == BasketCause.SORT_EXPOSURE
                        || c.getCause() == BasketCause.INCLUSIVE)) {
                    assessment(rv, batch, AssessmentTarget.SORT_TEAM, sortTeam,
                            AssessmentResult.DEDUCT, new BigDecimal("5"), BigDecimal.ZERO,
                            "混合原因中部分坏果来自分拣台长时间暴露，列入分拣班组考核", reviewer);
                }
                if (clues.stream().anyMatch(c -> c.getCause() == BasketCause.ARRIVAL_QUALITY
                        || c.getCause() == BasketCause.INCLUSIVE)) {
                    assessment(rv, batch, AssessmentTarget.QC_INSPECTOR, qcName,
                            AssessmentResult.NEUTRAL, BigDecimal.ZERO, BigDecimal.ZERO,
                            "混合原因中含来货质量成分，已向货主划责；质检员备案无责记录", reviewer);
                }
            }
        }
    }

    private boolean qcMissedArrivalQuality(Inspection qc) {
        // 简化判定：质检完全无记录视为漏检；有腐损率记录视为已发现
        return qc == null;
    }

    private void assessment(LossReview rv, Batch batch, AssessmentTarget target, String name,
                            AssessmentResult result, BigDecimal score, BigDecimal penalty,
                            String reason, String operator) {
        SortingAssessment a = new SortingAssessment();
        a.setReviewId(rv.getId());
        a.setBatchId(batch.getId());
        a.setTarget(target);
        a.setTargetName(name);
        a.setResult(result);
        a.setScoreChange(score);
        a.setPenaltyAmount(penalty == null ? BigDecimal.ZERO : penalty);
        a.setReason(reason);
        a.setOperator(operator);
        assessmentRepository.save(a);
    }

    @Transactional
    public CustomerCompensation payCompensation(Long id, String operator) {
        CustomerCompensation cc = compensationRepository.findById(id)
                .orElseThrow(() -> new BizException("客户赔付记录不存在"));
        cc.setStatus("已赔付");
        cc.setOperator(operator == null ? cc.getOperator() : operator);
        cc.setCompensatedAt(LocalDateTime.now());
        compensationRepository.save(cc);
        LossReview rv = reviewRepository.findById(cc.getReviewId()).orElse(null);
        if (rv != null) {
            traceService.log(cc.getBatchId(), Stage.COMPLAINT,
                    "客户赔付到账 " + cc.getCompensationNo(),
                    "复核单 " + rv.getReviewNo() + " 项下对客赔付 " + cc.getAmount()
                            + " 元已支付。" + nz(cc.getReason()), operator == null ? "财务" : operator);
        }
        return cc;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<LossReview> list() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<LossReview> openList() {
        return reviewRepository.findByStatusNotOrderByCreatedAtDesc(ReviewStatus.CONFIRMED)
                .stream().filter(r -> r.getStatus() != ReviewStatus.CANCELLED).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        LossReview rv = mustGet(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("review", rv);
        m.put("baskets", basketRepository.findByReviewIdOrderBySortStationAtAsc(id));
        m.put("assessments", assessmentRepository.findByReviewIdOrderByIdAsc(id));
        m.put("compensations", compensationRepository.findByReviewIdOrderByCreatedAtDesc(id));
        SortingRecord rec = sortingRepository.findById(rv.getSortingId()).orElse(null);
        m.put("sorting", rec);
        if (rec != null) {
            m.put("sortItems", sortItemRepository.findBySortingId(rec.getId()));
        }
        Batch b = batchRepository.findById(rv.getBatchId()).orElse(null);
        m.put("batchNo", b == null ? "" : b.getBatchNo());
        return m;
    }

    @Transactional(readOnly = true)
    public List<SortingAssessment> assessments() {
        return assessmentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<CustomerCompensation> compensations() {
        return compensationRepository.findAllByOrderByCreatedAtDesc();
    }

    // ---------------- 辅助 ----------------

    private LossReview mustGet(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> new BizException("复核单不存在: " + id));
    }

    private void ensureOpen(LossReview rv) {
        if (rv.getStatus() == ReviewStatus.CONFIRMED) {
            throw new BizException("复核单 " + rv.getReviewNo() + " 已出具结论，不能修改");
        }
        if (rv.getStatus() == ReviewStatus.CANCELLED) {
            throw new BizException("复核单已撤销");
        }
    }

    private String buildGradeBreakdown(Long sortingId) {
        StringBuilder sb = new StringBuilder();
        for (SortItem it : sortItemRepository.findBySortingId(sortingId)) {
            sb.append(it.getGrade().desc).append(' ').append(it.getWeight()).append("kg @ ")
                    .append(it.getUnitPrice()).append(" 元/kg → ").append(it.getZoneCode())
                    .append("；");
        }
        return sb.toString();
    }

    private BigDecimal avgSortPrice(Long sortingId) {
        BigDecimal w = BigDecimal.ZERO;
        BigDecimal v = BigDecimal.ZERO;
        for (SortItem it : sortItemRepository.findBySortingId(sortingId)) {
            w = w.add(it.getWeight());
            v = v.add(it.getWeight().multiply(nz(it.getUnitPrice())));
        }
        if (w.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return v.divide(w, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String nz(String s) { return s == null ? "" : s; }
    private String percent(BigDecimal v) {
        if (v == null) return "-";
        return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }
    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
