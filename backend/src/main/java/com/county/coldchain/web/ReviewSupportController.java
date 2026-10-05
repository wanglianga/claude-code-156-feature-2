package com.county.coldchain.web;

import com.county.coldchain.domain.entity.CrateHandlingRecord;
import com.county.coldchain.domain.entity.CustomerCompensation;
import com.county.coldchain.domain.entity.InspectorPerformance;
import com.county.coldchain.service.LossReviewService;
import com.county.coldchain.web.dto.CrateHandlingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "争议复核配套：周转筐时间线/质检员考核/客户赔付")
@RestController
@RequestMapping("/api/review-support")
@RequiredArgsConstructor
public class ReviewSupportController {

    private final LossReviewService reviewService;

    @Operation(summary = "录入周转筐装卸/搬运时间线（筐号、工位、到货/卸车/到台/开拣时间、堆层）")
    @PostMapping("/crate-handling")
    public CrateHandlingRecord saveHandling(@RequestBody CrateHandlingRequest req) {
        return reviewService.saveHandling(req);
    }

    @Operation(summary = "按批次查询周转筐搬运时间线")
    @GetMapping("/crate-handling/batch/{batchId}")
    public List<CrateHandlingRecord> handlingByBatch(@PathVariable Long batchId) {
        return reviewService.handlingByBatch(batchId);
    }

    @Operation(summary = "质检员考核流水（复核结案回写）")
    @GetMapping("/inspector-performance")
    public List<InspectorPerformance> performance() {
        return reviewService.performanceList();
    }

    @Operation(summary = "质检员考核汇总（累计扣分、漏检次数、当前分值）")
    @GetMapping("/inspector-performance/summary")
    public List<Map<String, Object>> performanceSummary() {
        return reviewService.performanceSummary();
    }

    @Operation(summary = "客户/货主赔付记录台账（冷库责任分拣操作不当）")
    @GetMapping("/compensations")
    public List<CustomerCompensation> compensations() {
        return reviewService.compensationList();
    }

    @Operation(summary = "分拣班组考核汇总（按责任班组统计冷库责任件数/承担重量/赔付金额）")
    @GetMapping("/team-performance")
    public List<Map<String, Object>> teamPerformance() {
        return reviewService.teamSummary();
    }
}
