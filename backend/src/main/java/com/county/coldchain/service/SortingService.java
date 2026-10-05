package com.county.coldchain.service;

import com.county.coldchain.domain.DomainEnums.AlertLevel;
import com.county.coldchain.domain.DomainEnums.AlertType;
import com.county.coldchain.domain.DomainEnums.BatchStatus;
import com.county.coldchain.domain.DomainEnums.Grade;
import com.county.coldchain.domain.DomainEnums.Stage;
import com.county.coldchain.domain.entity.Batch;
import com.county.coldchain.domain.entity.Category;
import com.county.coldchain.domain.entity.SortItem;
import com.county.coldchain.domain.entity.SortingRecord;
import com.county.coldchain.domain.entity.StockLot;
import com.county.coldchain.repo.BatchRepository;
import com.county.coldchain.repo.CategoryRepository;
import com.county.coldchain.repo.SortItemRepository;
import com.county.coldchain.repo.SortingRecordRepository;
import com.county.coldchain.repo.StockLotRepository;
import com.county.coldchain.support.BizException;
import com.county.coldchain.web.dto.SortingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 分拣处理：按等级称重定价 → 决定库位温区、损耗率、售价与货主结算。
 * 损耗率超阈自动预警；货主争议时报损暂挂，须裁定后才进结算。
 */
@Service
@RequiredArgsConstructor
public class SortingService {

    private final SortingRecordRepository sortingRepository;
    private final SortItemRepository sortItemRepository;
    private final StockLotRepository stockLotRepository;
    private final BatchRepository batchRepository;
    private final CategoryRepository categoryRepository;
    private final AlertService alertService;
    private final TraceService traceService;

    @Value("${app.billing.loss-rate-warn:0.05}")
    private BigDecimal lossWarn;
    @Value("${app.billing.loss-rate-critical:0.10}")
    private BigDecimal lossCritical;

    @Transactional
    public SortingRecord sort(SortingRequest req) {
        Batch batch = batchRepository.findById(req.getBatchId())
                .orElseThrow(() -> new BizException("批次不存在"));
        if (batch.getStatus() != BatchStatus.SORTING && batch.getStatus() != BatchStatus.INSPECTED) {
            throw new BizException("当前批次状态不允许分拣：" + batch.getStatus().desc);
        }
        Category cat = categoryRepository.findById(batch.getCategoryId()).orElseThrow();

        BigDecimal input = req.getInputWeight();
        if (input.signum() <= 0) {
            throw new BizException("分拣投入重量必须大于 0");
        }
        BigDecimal remaining = batch.getNetWeight()
                .subtract(batch.getAcceptedWeight())
                .subtract(batch.getLossWeight())
                .subtract(batch.getReturnedWeight());
        if (input.compareTo(remaining) > 0) {
            throw new BizException("分拣投入 " + input + "kg 超过批次剩余可处理重量 " + remaining + "kg");
        }

        BigDecimal output = req.getItems().stream()
                .map(i -> nz(i.getWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (output.compareTo(input) > 0) {
            throw new BizException("分拣各等级合计 " + output + "kg 超过投入重量 " + input + "kg");
        }
        BigDecimal loss = input.subtract(output);
        BigDecimal lossRate = input.signum() == 0 ? BigDecimal.ZERO
                : loss.divide(input, 4, RoundingMode.HALF_UP);

        SortingRecord rec = new SortingRecord();
        rec.setSortingNo("S" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", sortingRepository.count() + 1));
        rec.setBatchId(batch.getId());
        rec.setSorter(req.getSorter() == null ? "分拣员" : req.getSorter());
        rec.setSortedAt(LocalDateTime.now());
        rec.setInputWeight(input);
        rec.setOutputWeight(output);
        rec.setLossWeight(loss);
        rec.setLossRate(lossRate);
        rec.setLaborMinutes(req.getLaborMinutes() == null ? 0 : req.getLaborMinutes());
        rec.setDisputed(Boolean.TRUE.equals(req.getDisputed()));
        rec.setDisputeNote(req.getDisputeNote());
        rec.setDisputeResolved(!Boolean.TRUE.equals(req.getDisputed()));
        rec.setSortVideoUrl(req.getSortVideoUrl());
        rec.setBadFruitPhotos(req.getBadFruitPhotos());
        rec.setSortStation(req.getSortStation());
        rec.setRemark(req.getRemark());
        rec = sortingRepository.save(rec);

        StringBuilder sb = new StringBuilder();
        for (SortingRequest.Item it : req.getItems()) {
            Grade g = Grade.valueOf(it.getGrade());
            if (nz(it.getWeight()).signum() <= 0) {
                continue;
            }
            SortItem si = new SortItem();
            si.setSortingId(rec.getId());
            si.setGrade(g);
            si.setWeight(it.getWeight());
            si.setUnitPrice(it.getUnitPrice());
            si.setShelfLifeDays(it.getShelfLifeDays() == null ? defaultShelfLife(g) : it.getShelfLifeDays());
            si.setZoneCode(it.getZoneCode() == null ? cat.getRecommendedZone().name() : it.getZoneCode());
            sortItemRepository.save(si);

            StockLot lot = new StockLot();
            lot.setLotNo("L" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", stockLotRepository.count() + 1));
            lot.setBatchId(batch.getId());
            lot.setGrade(g);
            lot.setInitialWeight(it.getWeight());
            lot.setCurrentWeight(it.getWeight());
            lot.setUnitPrice(it.getUnitPrice());
            lot.setShelfLifeDays(si.getShelfLifeDays());
            lot.setInAt(LocalDateTime.now());
            lot.setStatus("PENDING");
            stockLotRepository.save(lot);
            sb.append(g.desc).append(' ').append(it.getWeight()).append("kg@").append(it.getUnitPrice())
                    .append("元 → ").append(si.getZoneCode()).append("；");
        }
        batch.setAcceptedWeight(batch.getAcceptedWeight().add(output));
        // 争议损耗先挂账，裁定后再回写批次损失重量
        if (!rec.getDisputed()) {
            batch.setLossWeight(batch.getLossWeight().add(loss));
        }
        batch.setStatus(BatchStatus.INSPECTED);
        batchRepository.save(batch);

        traceService.log(batch.getId(), null, null, Stage.SORTING,
                "分拣完成 " + rec.getSortingNo(),
                String.format("投入 %skg，产出 %skg，损耗 %skg（%s），工时 %s 分钟。%s",
                        input, output, loss, percent(lossRate), rec.getLaborMinutes(), sb),
                rec.getSorter());

        // ---- 风控 ----
        if (rec.getDisputed()) {
            alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.CRITICAL, batch.getId(), null,
                    "分拣损耗争议 " + batch.getBatchNo(),
                    String.format("货主不认可 %skg（%s）损耗，报损暂挂，备注：%s。需管理端裁定。",
                            loss, percent(lossRate), rec.getDisputeNote()),
                    rec.getSorter());
        } else if (lossRate.compareTo(lossCritical) >= 0) {
            alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.CRITICAL, batch.getId(), null,
                    "分拣损耗率严重偏高 " + batch.getBatchNo(),
                    "损耗率 " + percent(lossRate) + " ≥ 红线 " + percent(lossCritical)
                            + "，请核查温控与操作责任。", rec.getSorter());
        } else if (lossRate.compareTo(lossWarn) >= 0) {
            alertService.raise(AlertType.LOSS_DISPUTE, AlertLevel.WARN, batch.getId(), null,
                    "分拣损耗率预警 " + batch.getBatchNo(),
                    "损耗率 " + percent(lossRate) + " ≥ 预警线 " + percent(lossWarn), rec.getSorter());
        }
        return rec;
    }

    /** 管理端裁定损耗争议：认可冷库口径则计入批次损耗；改判则按裁定损耗调整可售/货值。 */
    @Transactional
    public SortingRecord resolveDispute(Long sortingId, boolean acceptLoss, BigDecimal adjudicatedLoss,
                                        String note) {
        SortingRecord rec = sortingRepository.findById(sortingId)
                .orElseThrow(() -> new BizException("分拣单不存在"));
        if (!rec.getDisputed() || rec.getDisputeResolved()) {
            throw new BizException("该分拣单无待裁定争议");
        }
        Batch batch = batchRepository.findById(rec.getBatchId()).orElseThrow();
        BigDecimal finalLoss = acceptLoss ? rec.getLossWeight()
                : (adjudicatedLoss == null ? BigDecimal.ZERO : adjudicatedLoss);
        if (finalLoss.compareTo(rec.getLossWeight()) > 0) {
            throw new BizException("裁定损耗不能超过登记损耗");
        }
        rec.setDisputeResolved(true);
        rec.setAdjudicatedLossWeight(finalLoss);
        rec.setDisputeNote((rec.getDisputeNote() == null ? "" : rec.getDisputeNote())
                + "｜裁定：" + (note == null ? "" : note) + "，最终损耗 " + finalLoss + "kg");
        sortingRepository.save(rec);
        batch.setLossWeight(batch.getLossWeight().add(finalLoss));
        // 冷库承担的差额（不认可的损耗）→ 视为可售回补，但实物已损，账单按原重补偿处理
        BigDecimal back = rec.getLossWeight().subtract(finalLoss);
        if (back.signum() > 0) {
            batch.setAcceptedWeight(batch.getAcceptedWeight().add(back));
        }
        batchRepository.save(batch);
        traceService.log(batch.getId(), null, null, Stage.SORTING,
                "损耗争议裁定 " + rec.getSortingNo(),
                "货主" + (acceptLoss ? "认可" : "部分认可") + "损耗，裁定损耗 " + finalLoss
                        + "kg，冷库补偿回补 " + back + "kg。" + (note == null ? "" : note),
                "管理端");
        return rec;
    }

    @Transactional(readOnly = true)
    public List<SortingRecord> listByBatch(Long batchId) {
        return sortingRepository.findByBatchIdOrderBySortedAtAsc(batchId);
    }

    @Transactional(readOnly = true)
    public List<SortItem> items(Long sortingId) {
        return sortItemRepository.findBySortingId(sortingId);
    }

    private int defaultShelfLife(Grade g) {
        return switch (g) {
            case A -> 5;
            case B -> 3;
            case PROCESS -> 2;
        };
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String percent(BigDecimal v) {
        return v.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
